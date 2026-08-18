package com.chatApplication.message_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @OneToOne
    @JoinColumn(name = "")
    private Long senderId;
    @Column
    @NotNull
    private Long recieverId;
    @Column(length = 10000)
    @NotNull
    private String content;
    @Column
    private LocalDateTime createdAt;
}
