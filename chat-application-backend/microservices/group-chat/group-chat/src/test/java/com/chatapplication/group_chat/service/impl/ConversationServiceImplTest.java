package com.chatapplication.group_chat.service.impl;

import com.chatapplication.group_chat.dto.response.ConversationResponse;
import com.chatapplication.group_chat.entitty.Conversation;
import com.chatapplication.group_chat.exception.ConversationNotFoundException;
import com.chatapplication.group_chat.mapper.ConversationMapper;
import com.chatapplication.group_chat.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationMapper conversationMapper;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    private Conversation conversation;
    private ConversationResponse conversationResponse;

    @BeforeEach
    void setUp() {
        conversation = Conversation.builder()
                .id(1L)
                .user1Id(10L)
                .user2Id(20L)
                .archived(false)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        conversationResponse = ConversationResponse.builder()
                .conversationId(1L)
                .userId(10L)
                .build();
    }

    @Test
    void createConversation_shouldSaveAndReturn() {
        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(conversationMapper.toResponse(any(Conversation.class))).thenReturn(conversationResponse);

        ConversationResponse result = conversationService.createConversation(10L, 20L);

        assertNotNull(result);
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void createConversation_whenExists_shouldReturnExisting() {
        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.of(conversation));
        when(conversationMapper.toResponse(conversation)).thenReturn(conversationResponse);

        ConversationResponse result = conversationService.createConversation(10L, 20L);

        assertNotNull(result);
        assertEquals(1L, result.getConversationId());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void getConversation_shouldReturnConversation() {
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(conversationMapper.toResponse(conversation)).thenReturn(conversationResponse);

        ConversationResponse result = conversationService.getConversation(1L);

        assertNotNull(result);
        assertEquals(1L, result.getConversationId());
    }

    @Test
    void getConversation_whenNotFound_shouldThrow() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ConversationNotFoundException.class,
                () -> conversationService.getConversation(999L));
    }

    @Test
    void getConversationBetweenUsers_shouldReturnConversation() {
        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.of(conversation));
        when(conversationMapper.toResponse(conversation)).thenReturn(conversationResponse);

        ConversationResponse result = conversationService.getConversationBetweenUsers(10L, 20L);

        assertNotNull(result);
        assertEquals(1L, result.getConversationId());
    }

    @Test
    void getConversationBetweenUsers_whenNotFound_shouldThrow() {
        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.empty());

        assertThrows(ConversationNotFoundException.class,
                () -> conversationService.getConversationBetweenUsers(10L, 20L));
    }

    @Test
    void getUserConversations_shouldReturnList() {
        when(conversationRepository.findBySenderIdOrReceiverId(10L, 10L))
                .thenReturn(List.of(conversation));
        when(conversationMapper.toResponseList(anyList()))
                .thenReturn(List.of(conversationResponse));

        List<ConversationResponse> result = conversationService.getUserConversations(10L);

        assertEquals(1, result.size());
    }

    @Test
    void archiveConversation_shouldSetArchived() {
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);

        conversationService.archiveConversation(1L);

        assertTrue(conversation.isArchived());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void archiveConversation_whenNotFound_shouldThrow() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ConversationNotFoundException.class,
                () -> conversationService.archiveConversation(999L));
    }

    @Test
    void unarchiveConversation_shouldSetUnarchived() {
        conversation.setArchived(true);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);

        conversationService.unarchiveConversation(1L);

        assertFalse(conversation.isArchived());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void unarchiveConversation_whenNotFound_shouldThrow() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ConversationNotFoundException.class,
                () -> conversationService.unarchiveConversation(999L));
    }

    @Test
    void deleteConversation_shouldSetDeleted() {
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);

        conversationService.deleteConversation(1L);

        assertTrue(conversation.isDeleted());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void deleteConversation_whenNotFound_shouldThrow() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ConversationNotFoundException.class,
                () -> conversationService.deleteConversation(999L));
    }

    @Test
    void restoreConversation_shouldSetDeletedFalse() {
        conversation.setDeleted(true);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);

        conversationService.restoreConversation(1L);

        assertFalse(conversation.isDeleted());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void restoreConversation_whenNotFound_shouldThrow() {
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ConversationNotFoundException.class,
                () -> conversationService.restoreConversation(999L));
    }

    @Test
    void exists_whenConversationExists_shouldReturnTrue() {
        when(conversationRepository.existsById(1L)).thenReturn(true);

        assertTrue(conversationService.exists(1L));
    }

    @Test
    void exists_whenConversationNotExists_shouldReturnFalse() {
        when(conversationRepository.existsById(999L)).thenReturn(false);

        assertFalse(conversationService.exists(999L));
    }

    @Test
    void countUserConversations_shouldReturnCount() {
        when(conversationRepository.findBySenderIdOrReceiverId(10L, 10L))
                .thenReturn(List.of(conversation));

        long count = conversationService.countUserConversations(10L);

        assertEquals(1L, count);
    }
}
