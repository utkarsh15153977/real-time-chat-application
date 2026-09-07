package com.chatApplication.user_service.service;

import com.chatApplication.user_service.dto.UpdateUserRequest;
import com.chatApplication.user_service.dto.UserResponse;
import com.chatApplication.user_service.entity.UserProfile;
import com.chatApplication.user_service.exception.UserNotFoundException;
import com.chatApplication.user_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserServiceImpl service;

    @Test
    void getUser_existingUser_returnsUserResponse() {
        Long userId = 1L;
        UserProfile user = UserProfile.builder()
                .userId(userId)
                .name("John Doe")
                .email("john@example.com")
                .phone("1234567890")
                .online(false)
                .build();

        when(repository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = service.getUser(userId);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals("John Doe", response.getName());
        assertEquals("john@example.com", response.getEmail());
        verify(repository, times(1)).findById(userId);
    }

    @Test
    void getUser_nonExistingUser_throwsUserNotFoundException() {
        Long userId = 99L;
        when(repository.findById(userId)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> service.getUser(userId)
        );

        assertTrue(exception.getMessage().contains("99"));
        verify(repository, times(1)).findById(userId);
    }

    @Test
    void updateUser_existingUser_returnsUpdatedUser() {
        Long userId = 1L;
        UserProfile user = UserProfile.builder()
                .userId(userId)
                .name("Old Name")
                .email("john@example.com")
                .phone("1234567890")
                .build();

        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("New Name");
        request.setPhone("0987654321");
        request.setProfilePicture("http://img.com/new.jpg");
        request.setStatusMessage("Hello World");

        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(any(UserProfile.class))).thenReturn(user);

        UserResponse response = service.updateUser(userId, request);

        assertNotNull(response);
        assertEquals("New Name", response.getName());
        assertEquals("0987654321", response.getPhone());
        assertEquals("http://img.com/new.jpg", response.getProfilePicture());
        assertEquals("Hello World", response.getStatusMessage());
        verify(repository, times(1)).save(any(UserProfile.class));
    }

    @Test
    void updateUser_nonExistingUser_throwsUserNotFoundException() {
        Long userId = 99L;
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Test");
        request.setPhone("123");

        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> service.updateUser(userId, request)
        );
        verify(repository, never()).save(any());
    }

    @Test
    void searchUser_matchingKeyword_returnsList() {
        String keyword = "John";
        List<UserProfile> users = List.of(
                UserProfile.builder().userId(1L).name("John Doe").email("john@example.com").phone("123").build(),
                UserProfile.builder().userId(2L).name("Johnny").email("johnny@example.com").phone("456").build()
        );

        when(repository.findByNameContainingIgnoreCase(keyword)).thenReturn(users);

        List<UserResponse> results = service.searchUser(keyword);

        assertEquals(2, results.size());
        assertEquals("John Doe", results.get(0).getName());
        assertEquals("Johnny", results.get(1).getName());
        verify(repository, times(1)).findByNameContainingIgnoreCase(keyword);
    }

    @Test
    void searchUser_noMatch_returnsEmptyList() {
        String keyword = "NonExistent";
        when(repository.findByNameContainingIgnoreCase(keyword)).thenReturn(List.of());

        List<UserResponse> results = service.searchUser(keyword);

        assertTrue(results.isEmpty());
    }

    @Test
    void updateStatus_existingUser_setsOnlineAndLastSeen() {
        Long userId = 1L;
        UserProfile user = UserProfile.builder()
                .userId(userId)
                .name("John Doe")
                .online(false)
                .build();

        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(any(UserProfile.class))).thenReturn(user);

        service.updateStatus(userId, true);

        assertTrue(user.getOnline());
        assertNotNull(user.getLastSeen());
        verify(repository, times(1)).save(user);
    }

    @Test
    void updateStatus_nonExistingUser_throwsUserNotFoundException() {
        Long userId = 99L;
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> service.updateStatus(userId, true)
        );
        verify(repository, never()).save(any());
    }
}
