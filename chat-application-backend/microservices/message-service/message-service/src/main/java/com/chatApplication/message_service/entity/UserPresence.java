package com.chatApplication.message_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Entity
@Table(name = "user_presence")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPresence {
    @Id
    private String userId;
    private boolean online;
    private LocalDateTime lastSeen;
}
