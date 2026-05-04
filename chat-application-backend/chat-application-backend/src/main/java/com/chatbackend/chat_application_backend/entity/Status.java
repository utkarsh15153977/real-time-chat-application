package com.chatbackend.chat_application_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "statuses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Status {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(length = 1000)
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    //Status Deleted automatically after 24 hours
//    @PrePersist is a JPA lifecycle annotation. It tells Hibernate/JPA
//    to execute a method automatically before an entity is inserted into the database.

    //Before save() happens → run this method first.
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        expiresAt = createdAt.plusHours(24);
    }
}
