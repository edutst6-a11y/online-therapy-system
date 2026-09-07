package com.mindcare.backend.repository;

import com.mindcare.backend.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderBySentAtAsc(UUID conversationId);

    long countByConversationIdAndReadAtIsNull(UUID conversationId);

    long countByConversationIdAndReadAtIsNullAndSenderIdNot(UUID conversationId, UUID senderId);

    List<Message> findByConversationIdAndReadAtIsNullAndSenderIdNot(UUID conversationId, UUID senderId);
}
