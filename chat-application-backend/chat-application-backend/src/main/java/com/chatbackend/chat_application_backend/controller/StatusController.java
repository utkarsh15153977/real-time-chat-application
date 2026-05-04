package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.entity.Status;
import com.chatbackend.chat_application_backend.service.StatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/status")
public class StatusController {
    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    // Upload status
    @PostMapping
    public ResponseEntity<Status> uploadStatus(@RequestBody Status status) {
        Status createdStatus = statusService.uploadStatus(status);
        return ResponseEntity.ok(createdStatus);
    }

    // Get status by user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Status>> getUserStatus(@PathVariable Long userId) {
        return ResponseEntity.ok(statusService.getUserStatus(userId));
    }

    // Get active status (within 24 hours)
    @GetMapping("/active")
    public ResponseEntity<List<Status>> getActiveStatus() {
        return ResponseEntity.ok(statusService.getActiveStatus());
    }

    // Get single status
    @GetMapping("/{statusId}")
    public ResponseEntity<Status> getStatus(@PathVariable Long statusId) {
        return ResponseEntity.ok(statusService.getStatus(statusId));
    }

    // Delete status
    @DeleteMapping("/{statusId}")
    public ResponseEntity<Void> deleteStatus(@PathVariable Long statusId) {
        statusService.deleteStatus(statusId);
        return ResponseEntity.noContent().build();
    }

    // Get status of user contacts
    @GetMapping("/contacts/{userId}")
    public ResponseEntity<List<Status>> getStatusOfContacts(@PathVariable Long userId) {
        return ResponseEntity.ok(statusService.getStatusOfContacts(userId));
    }

    // Mark status as viewed
    @PostMapping("/{statusId}/view/{viewerId}")
    public ResponseEntity<Void> markStatusViewed(@PathVariable Long statusId,
                                                 @PathVariable Long viewerId) {
        statusService.markStatusViewed(statusId, viewerId);
        return ResponseEntity.ok().build();
    }
}
