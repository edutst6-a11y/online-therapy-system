package com.mindcare.backend.messaging;

import com.mindcare.backend.messaging.dto.ConversationSummary;
import com.mindcare.backend.messaging.dto.MessageResponse;
import com.mindcare.backend.messaging.dto.SendMessageRequest;
import com.mindcare.backend.messaging.dto.StartConversationRequest;
import com.mindcare.backend.model.Conversation;
import com.mindcare.backend.model.Message;
import com.mindcare.backend.model.NotificationType;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.notification.NotificationService;
import com.mindcare.backend.repository.AppointmentRepository;
import com.mindcare.backend.repository.ConversationRepository;
import com.mindcare.backend.repository.MessageRepository;
import com.mindcare.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Direct messaging is scoped to a client and the therapist they have an actual
 * appointment history with — a conversation cannot be started with anyone else.
 * This is not an emergency channel; that copy lives in the frontend, next to
 * the message composer.
 */
@RestController
@RequestMapping("/api/conversations")
@Transactional
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    public ConversationController(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            AppointmentRepository appointmentRepository,
            NotificationService notificationService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST')")
    public ConversationSummary start(@Valid @RequestBody StartConversationRequest request, @AuthenticationPrincipal User user) {
        User other = userRepository.findById(request.otherUserId())
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        User client;
        User therapist;
        if (user.getRole() == Role.CLIENT) {
            if (other.getRole() != Role.THERAPIST) {
                throw new IllegalArgumentException("Clients can only message their therapist");
            }
            client = user;
            therapist = other;
        } else {
            if (other.getRole() != Role.CLIENT) {
                throw new IllegalArgumentException("Therapists can only message their clients");
            }
            client = other;
            therapist = user;
        }

        if (!appointmentRepository.existsByClientIdAndTherapistId(client.getId(), therapist.getId())) {
            throw new AccessDeniedException("No appointment history between these two users");
        }

        Conversation conversation = conversationRepository.findByClientIdAndTherapistId(client.getId(), therapist.getId())
                .orElseGet(() -> conversationRepository.save(new Conversation(client, therapist)));

        long unread = messageRepository.countByConversationIdAndReadAtIsNullAndSenderIdNot(conversation.getId(), user.getId());
        return ConversationSummary.from(conversation, unread);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST')")
    public List<ConversationSummary> mine(@AuthenticationPrincipal User user) {
        List<Conversation> conversations = user.getRole() == Role.CLIENT
                ? conversationRepository.findByClientIdOrderByCreatedAtDesc(user.getId())
                : conversationRepository.findByTherapistIdOrderByCreatedAtDesc(user.getId());

        return conversations.stream()
                .map(c -> ConversationSummary.from(c, messageRepository.countByConversationIdAndReadAtIsNullAndSenderIdNot(c.getId(), user.getId())))
                .toList();
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST')")
    public List<MessageResponse> messages(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Conversation conversation = ownConversationOrThrow(id, user);

        List<Message> unread = messageRepository.findByConversationIdAndReadAtIsNullAndSenderIdNot(conversation.getId(), user.getId());
        unread.forEach(Message::markRead);
        messageRepository.saveAll(unread);

        return messageRepository.findByConversationIdOrderBySentAtAsc(conversation.getId()).stream()
                .map(MessageResponse::from)
                .toList();
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST')")
    public MessageResponse send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request, @AuthenticationPrincipal User user) {
        Conversation conversation = ownConversationOrThrow(id, user);
        Message message = new Message(conversation, user, request.body());
        messageRepository.save(message);

        User recipient = conversation.getClient().getId().equals(user.getId()) ? conversation.getTherapist() : conversation.getClient();
        String preview = request.body().length() > 200 ? request.body().substring(0, 197) + "..." : request.body();
        notificationService.notify(recipient, NotificationType.NEW_MESSAGE,
                "New message from " + user.getFullName(), preview, conversation.getId());

        return MessageResponse.from(message);
    }

    private Conversation ownConversationOrThrow(UUID id, User user) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));
        boolean participant = conversation.getClient().getId().equals(user.getId())
                || conversation.getTherapist().getId().equals(user.getId());
        if (!participant) {
            throw new AccessDeniedException("Not your conversation");
        }
        return conversation;
    }
}
