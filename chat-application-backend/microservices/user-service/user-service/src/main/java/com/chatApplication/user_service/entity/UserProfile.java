package com.chatApplication.user_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {
    @Id
    private Long userId;
    @Column(nullable=false)
    private String name;
    @Column(nullable=false,unique=true)
    private String email;
    @Column(nullable=false,unique=true)
    private String phone;
    private String profilePicture;
    private String statusMessage;
    @Builder.Default
    private Boolean online=false;
    private LocalDateTime lastSeen;


}
