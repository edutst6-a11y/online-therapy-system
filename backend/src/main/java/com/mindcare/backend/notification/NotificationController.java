package com.mindcare.backend.notification;

import com.mindcare.backend.model.Notification;
import com.mindcare.backend.model.User;
import com.mindcare.backend.notification.dto.NotificationResponse;
import com.mindcare.backend.repository.NotificationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Every role reads only their own notifications — there is no clinic-wide notification feed. */
@RestController
@RequestMapping("/api/notifications")
@Transactional
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/mine")
    public List<NotificationResponse> mine(@AuthenticationPrincipal User user) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", notificationRepository.countByRecipientIdAndReadAtIsNull(user.getId()));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Notification not found"));
        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your notification");
        }
        notification.markRead();
        notificationRepository.save(notification);
        return NotificationResponse.from(notification);
    }

    @PatchMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal User user) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndReadAtIsNull(user.getId());
        unread.forEach(Notification::markRead);
        notificationRepository.saveAll(unread);
    }
}
