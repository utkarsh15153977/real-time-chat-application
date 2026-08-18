package com.chatapp.auth_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Entity
@Table(name = "blacklisted_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000, unique = true)
    private String token;

    private LocalDateTime blacklistedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt; // To auto-clean expired tokens

    @Column(name = "blacklisted_by")
    private String blacklistedBy; // Which user's token

    @Column(name = "reason")
    private String reason; // LOGOUT, COMPROMISED, ADMIN_BLOCK
}
