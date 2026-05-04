package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.entity.Reaction;
import com.chatbackend.chat_application_backend.service.ReactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reactions")
public class ReactionController {
    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    // Add reaction to a message
    @PostMapping
    public ResponseEntity<Reaction> addReaction(@RequestParam Long messageId,
                                                @RequestParam Long userId,
                                                @RequestParam String reactionType) {
        Reaction reaction = reactionService.addReaction(messageId, userId, reactionType);
        return ResponseEntity.ok(reaction);
    }

    // Remove reaction
    @DeleteMapping
    public ResponseEntity<Void> removeReaction(@RequestParam Long messageId,
                                               @RequestParam Long userId) {
        reactionService.removeReaction(messageId, userId);
        return ResponseEntity.noContent().build();
    }

    // Update reaction
    @PutMapping
    public ResponseEntity<Reaction> updateReaction(@RequestParam Long messageId,
                                                   @RequestParam Long userId,
                                                   @RequestParam String reactionType) {
        Reaction reaction = reactionService.updateReaction(messageId, userId, reactionType);
        return ResponseEntity.ok(reaction);
    }

    // Get reactions for a message
    @GetMapping("/message/{messageId}")
    public ResponseEntity<List<Reaction>> getReactionsByMessage(@PathVariable Long messageId) {
        List<Reaction> reactions = reactionService.getReactionsByMessage(messageId);
        return ResponseEntity.ok(reactions);
    }

    // Get reactions by a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Reaction>> getReactionsByUser(@PathVariable Long userId) {
        List<Reaction> reactions = reactionService.getReactionsByUser(userId);
        return ResponseEntity.ok(reactions);
    }

    // Count reactions on a message
    @GetMapping("/message/{messageId}/count")
    public ResponseEntity<Long> countReactions(@PathVariable Long messageId) {
        long count = reactionService.countReactionsByMessage(messageId);
        return ResponseEntity.ok(count);
    }
}
