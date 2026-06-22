package com.chatApplication.chat_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="chat_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMember {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
    private Long chatId;
    private Long userId;
    private Boolean admin;
    private LocalDateTime joinedAt;

    @PrePersist
    public void create(){

        joinedAt= LocalDateTime.now();

    }
}
