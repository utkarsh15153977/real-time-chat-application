package com.chatApplication.chat_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="chat_members",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"chat_id", "user_id"})
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMember {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder.Default
    private Boolean admin = false;

    private LocalDateTime joinedAt;

    @PrePersist
    public void create(){
        joinedAt = LocalDateTime.now();
        if (admin == null) {
            admin = false;
        }
    }
}
