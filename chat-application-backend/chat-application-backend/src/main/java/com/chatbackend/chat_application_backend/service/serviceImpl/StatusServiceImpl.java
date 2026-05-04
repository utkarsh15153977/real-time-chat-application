package com.chatbackend.chat_application_backend.service.serviceImpl;

import com.chatbackend.chat_application_backend.entity.Status;
import com.chatbackend.chat_application_backend.repository.StatusRepository;
import com.chatbackend.chat_application_backend.service.StatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class StatusServiceImpl implements StatusService {
    private final StatusRepository statusRepository;

    public StatusServiceImpl(StatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    @Override
    public Status uploadStatus(Status status) {
        return statusRepository.save(status);
    }

    @Override
    public List<Status> getUserStatus(Long userId) {
        return statusRepository.findByUserId(userId);
    }

    @Override
    public List<Status> getActiveStatus() {
        return statusRepository.findByExpiresAtAfter(LocalDateTime.now());
    }

    @Override
    public void deleteStatus(Long statusId) {
        Status status = getStatus(statusId);
        statusRepository.delete(status);
    }

    // Get single status
    @Override
    public Status getStatus(Long statusId) {
        return statusRepository.findById(statusId)
                .orElseThrow(() -> new RuntimeException("Status not found"));
    }

    // Get status of contacts (basic implementation)
    @Override
    public List<Status> getStatusOfContacts(Long userId) {
        return statusRepository.findByExpiresAtAfter(LocalDateTime.now());
    }

    // Mark status as viewed
    @Override
    public void markStatusViewed(Long statusId, Long viewerId) {
        Status status = getStatus(statusId);
        System.out.println("User " + viewerId + " viewed status " + status.getId());
    }
}
