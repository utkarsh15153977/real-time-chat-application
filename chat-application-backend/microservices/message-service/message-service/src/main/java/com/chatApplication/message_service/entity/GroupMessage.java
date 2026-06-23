package com.chatApplication.message_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long messageId;
    private Long groupId;
    private String senderId;
    @Column(length = 5000)
    private String content;
    private LocalDateTime sentAt;
    @PrePersist
    public void prePersist() {
        sentAt = LocalDateTime.now();
    }
}
