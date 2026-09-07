package com.chatApplication.user_service.repository;

import com.chatApplication.user_service.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Test
    void save_andFindById_returnsUser() {
        UserProfile user = UserProfile.builder()
                .userId(1L)
                .name("John Doe")
                .email("john@example.com")
                .phone("1234567890")
                .online(false)
                .build();

        repository.save(user);

        Optional<UserProfile> found = repository.findById(1L);

        assertTrue(found.isPresent());
        assertEquals("John Doe", found.get().getName());
        assertEquals("john@example.com", found.get().getEmail());
    }

    @Test
    void findByEmail_existingEmail_returnsUser() {
        UserProfile user = UserProfile.builder()
                .userId(2L)
                .name("Jane Doe")
                .email("jane@example.com")
                .phone("0987654321")
                .build();

        repository.save(user);

        Optional<UserProfile> found = repository.findByEmail("jane@example.com");

        assertTrue(found.isPresent());
        assertEquals("Jane Doe", found.get().getName());
    }

    @Test
    void findByEmail_nonExistingEmail_returnsEmpty() {
        Optional<UserProfile> found = repository.findByEmail("nonexistent@example.com");

        assertFalse(found.isPresent());
    }

    @Test
    void findByNameContainingIgnoreCase_returnsMatchingUsers() {
        repository.save(UserProfile.builder()
                .userId(3L).name("Alice Smith").email("alice@example.com").phone("111").build());
        repository.save(UserProfile.builder()
                .userId(4L).name("Bob Alice Johnson").email("bob@example.com").phone("222").build());
        repository.save(UserProfile.builder()
                .userId(5L).name("Charlie Brown").email("charlie@example.com").phone("333").build());

        List<UserProfile> results = repository.findByNameContainingIgnoreCase("alice");

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(u -> u.getName().toLowerCase().contains("alice")));
    }

    @Test
    void findByNameContainingIgnoreCase_noMatch_returnsEmptyList() {
        List<UserProfile> results = repository.findByNameContainingIgnoreCase("NonExistent");

        assertTrue(results.isEmpty());
    }

    @Test
    void save_multipleUsers_allPersisted() {
        repository.save(UserProfile.builder()
                .userId(10L).name("User A").email("a@example.com").phone("100").build());
        repository.save(UserProfile.builder()
                .userId(11L).name("User B").email("b@example.com").phone("200").build());
        repository.save(UserProfile.builder()
                .userId(12L).name("User C").email("c@example.com").phone("300").build());

        List<UserProfile> all = repository.findAll();

        assertEquals(3, all.size());
    }
}
