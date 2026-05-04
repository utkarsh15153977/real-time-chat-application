package com.chatbackend.chat_application_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_phone", columnList = "phone"),
        @Index(name = "idx_users_name", columnList = "name")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(unique = true, nullable = false)
    private String phone;
    @JsonIgnore
    @Column(nullable = false)
    private String password;
    private String profilePicture;
    private Boolean online;
    private LocalDateTime timestamp;
    private String statusMessage;
    @PrePersist
    public void prePersist() {
        timestamp = LocalDateTime.now();
    }
}
