package com.chatbackend.chat_application_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Added Indexing for performance
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_chat_room_timestamp", columnList = "chat_room_id,timestamp"),
        @Index(name = "idx_messages_sender", columnList = "sender_id"),
        @Index(name = "idx_messages_status", columnList = "status")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    LocalDateTime timestamp;
    @PrePersist
    public void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    @Enumerated(EnumType.STRING)
    private MessageStatus status;
}
