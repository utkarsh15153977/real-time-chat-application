package com.chatbackend.chat_application_backend.service;

import com.chatbackend.chat_application_backend.entity.Status;

import java.util.List;

public interface StatusService {
    // Upload status
    Status uploadStatus(Status status);
    // Get status by user
    List<Status> getUserStatus(Long userId);
    // Get active status (within 24 hours)
    List<Status> getActiveStatus();
    // Delete status
    void deleteStatus(Long statusId);
    //Update Status
    Status getStatus(Long statusId);
    List<Status> getStatusOfContacts(Long userId);
    void markStatusViewed(Long statusId, Long viewerId);
}
