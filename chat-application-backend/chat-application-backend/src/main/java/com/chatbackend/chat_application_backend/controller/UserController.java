package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.dto.ProfilePictureRequest;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = userService.getUser(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User createdUser = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        User updatedUser = userService.updateUser(user);
        return ResponseEntity.ok(updatedUser);
    }

    @PostMapping("/{id}")
    public ResponseEntity<User> updateUserPost(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        User updatedUser = userService.updateUser(user);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String name) {
        List<User> users = userService.searchUsersByName(name);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}/contacts")
    public ResponseEntity<List<User>> getUserContacts(@PathVariable Long id) {
        List<User> contacts = userService.getUserContacts(id);
        return ResponseEntity.ok(contacts);
    }

    @PutMapping("/{id}/profile-picture")
    public ResponseEntity<User> updateProfilePicture(
            @PathVariable Long id,
            @RequestBody ProfilePictureRequest request) {

        User user = userService.updateProfilePicture(id, request.getPictureUrl());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<User> updateUserStatus(@PathVariable Long id, @RequestParam String statusMessage) {
        User user = userService.updateUserStatus(id, statusMessage);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/online")
    public ResponseEntity<Void> setUserOnline(@PathVariable Long id) {
        userService.setUserOnline(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/offline")
    public ResponseEntity<Void> setUserOffline(@PathVariable Long id) {
        userService.setUserOffline(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/online")
    public ResponseEntity<Boolean> isUserOnline(@PathVariable Long id) {
        boolean online = userService.isUserOnline(id);
        return ResponseEntity.ok(online);
    }
}
