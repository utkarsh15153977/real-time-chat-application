package com.chatapplication.group_chat.service.impl;

import com.chatapplication.group_chat.dto.request.ChatMessageRequest;
import com.chatapplication.group_chat.dto.request.EditMessageRequest;
import com.chatapplication.group_chat.dto.request.ReactionRequest;
import com.chatapplication.group_chat.dto.response.ChatMessageResponse;
import com.chatapplication.group_chat.dto.response.ReactionResponse;
import com.chatapplication.group_chat.entitty.*;
import com.chatapplication.group_chat.exception.*;
import com.chatapplication.group_chat.mapper.ChatMessageMapper;
import com.chatapplication.group_chat.mapper.MessageReactionMapper;
import com.chatapplication.group_chat.repository.ChatMessageRepository;
import com.chatapplication.group_chat.repository.ConversationRepository;
import com.chatapplication.group_chat.repository.MessageReactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private MessageReactionRepository messageReactionRepository;
    @Mock private MessageReactionMapper messageReactionMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageServiceImpl messageService;

    private Conversation conversation;
    private ChatMessage chatMessage;

    @BeforeEach
    void setUp() {
        conversation = Conversation.builder()
                .id(1L)
                .user1Id(10L)
                .user2Id(20L)
                .build();

        chatMessage = ChatMessage.builder()
                .id(1L)
                .conversation(conversation)
                .senderId(10L)
                .receiverId(20L)
                .content("Hello")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .edited(false)
                .deleted(false)
                .build();
    }

    @Test
    void sendMessage_shouldSaveAndReturnResponse() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(10L)
                .receiverId(20L)
                .content("Hello")
                .build();

        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(1L)
                .conversationId(1L)
                .senderId(10L)
                .receiverId(20L)
                .content("Hello")
                .build();

        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.of(conversation));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(response);

        ChatMessageResponse result = messageService.sendMessage(request);

        assertNotNull(result);
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void sendMessage_whenSenderIdNull_shouldThrow() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(null)
                .receiverId(20L)
                .content("Hello")
                .build();

        assertThrows(InvalidMessageException.class, () -> messageService.sendMessage(request));
    }

    @Test
    void sendMessage_whenReceiverIdNull_shouldThrow() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(10L)
                .receiverId(null)
                .content("Hello")
                .build();

        assertThrows(InvalidMessageException.class, () -> messageService.sendMessage(request));
    }

    @Test
    void sendMessage_whenContentEmpty_shouldThrow() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(10L)
                .receiverId(20L)
                .content("")
                .build();

        assertThrows(InvalidMessageException.class, () -> messageService.sendMessage(request));
    }

    @Test
    void sendMessage_whenNullRequest_shouldThrow() {
        assertThrows(InvalidMessageException.class, () -> messageService.sendMessage(null));
    }

    @Test
    void getMessage_shouldReturnMessage() {
        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(1L)
                .content("Hello")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageMapper.toResponse(chatMessage)).thenReturn(response);

        ChatMessageResponse result = messageService.getMessage(1L);

        assertNotNull(result);
        assertEquals(1L, result.getMessageId());
    }

    @Test
    void getMessage_whenNotFound_shouldThrow() {
        when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(MessageNotFoundException.class, () -> messageService.getMessage(999L));
    }

    @Test
    void editMessage_shouldUpdateAndReturn() {
        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(1L)
                .userId(10L)
                .content("Updated content")
                .build();

        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(1L)
                .content("Updated content")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(response);

        ChatMessageResponse result = messageService.editMessage(1L, request);

        assertNotNull(result);
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void editMessage_whenNotOwner_shouldThrow() {
        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(1L)
                .userId(999L)
                .content("Updated")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(UnauthorizedException.class, () -> messageService.editMessage(1L, request));
    }

    @Test
    void editMessage_whenDeleted_shouldThrow() {
        chatMessage.setDeleted(true);
        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(1L)
                .userId(10L)
                .content("Updated")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(MessageAlreadyDeletedException.class, () -> messageService.editMessage(1L, request));
    }

    @Test
    void editMessage_whenContentEmpty_shouldThrow() {
        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(1L)
                .userId(10L)
                .content("")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(InvalidMessageException.class, () -> messageService.editMessage(1L, request));
    }

    @Test
    void deleteMessage_shouldDelete() {
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);

        messageService.deleteMessage(1L, 10L);

        assertTrue(chatMessage.isDeleted());
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void deleteMessage_whenNotOwner_shouldThrow() {
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(UnauthorizedException.class, () -> messageService.deleteMessage(1L, 999L));
    }

    @Test
    void deleteMessage_whenAlreadyDeleted_shouldThrow() {
        chatMessage.setDeleted(true);
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(MessageAlreadyDeletedException.class, () -> messageService.deleteMessage(1L, 10L));
    }

    @Test
    void deleteMessage_whenNotFound_shouldThrow() {
        when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(MessageNotFoundException.class, () -> messageService.deleteMessage(999L, 10L));
    }

    @Test
    void markDelivered_shouldUpdateStatus() {
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(new ChatMessageResponse());

        messageService.markDelivered(1L);

        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void markDelivered_whenAlreadyDelivered_shouldNotSave() {
        chatMessage.setStatus(MessageStatus.DELIVERED);
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        messageService.markDelivered(1L);

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void markSeen_shouldUpdateStatus() {
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(new ChatMessageResponse());

        messageService.markSeen(1L);

        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void markSeen_whenAlreadySeen_shouldNotSave() {
        chatMessage.setStatus(MessageStatus.SEEN);
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        messageService.markSeen(1L);

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void markAllAsSeen_shouldMarkAllUnread() {
        ChatMessage unreadMsg = ChatMessage.builder()
                .id(2L)
                .conversation(conversation)
                .senderId(10L)
                .receiverId(20L)
                .status(MessageStatus.SENT)
                .build();

        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationAndReceiverIdAndStatus(
                conversation, 20L, MessageStatus.SENT))
                .thenReturn(List.of(unreadMsg));
        when(chatMessageRepository.saveAll(anyList())).thenReturn(List.of(unreadMsg));

        messageService.markAllAsSeen(10L, 20L);

        verify(chatMessageRepository).saveAll(anyList());
        assertEquals(MessageStatus.SEEN, unreadMsg.getStatus());
    }

    @Test
    void markAllAsSeen_whenNoConversation_shouldNotThrow() {
        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.empty());

        messageService.markAllAsSeen(10L, 20L);

        verify(chatMessageRepository, never()).saveAll(anyList());
    }

    @Test
    void getConversation_shouldReturnMessages() {
        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(1L)
                .content("Hello")
                .build();

        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.of(conversation));
        when(chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                .thenReturn(List.of(chatMessage));
        when(chatMessageMapper.toResponseList(anyList()))
                .thenReturn(List.of(response));

        List<ChatMessageResponse> result = messageService.getConversation(10L, 20L);

        assertEquals(1, result.size());
    }

    @Test
    void getConversation_whenNoConversation_shouldReturnEmptyList() {
        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.empty());

        List<ChatMessageResponse> result = messageService.getConversation(10L, 20L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getRecentMessages_shouldReturnMergedAndSorted() {
        ChatMessage sentMsg = ChatMessage.builder()
                .id(1L)
                .senderId(10L)
                .receiverId(20L)
                .createdAt(LocalDateTime.now())
                .build();

        ChatMessageResponse response = ChatMessageResponse.builder()
                .messageId(1L)
                .build();

        when(chatMessageRepository.findBySenderIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(sentMsg));
        when(chatMessageRepository.findByReceiverIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of());
        when(chatMessageMapper.toResponseList(anyList()))
                .thenReturn(List.of(response));

        List<ChatMessageResponse> result = messageService.getRecentMessages(10L);

        assertEquals(1, result.size());
    }

    @Test
    void getUnreadCount_shouldReturnCount() {
        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.of(conversation));
        when(chatMessageRepository.countByConversationAndReceiverIdAndStatus(
                conversation, 20L, MessageStatus.SENT))
                .thenReturn(5L);

        long result = messageService.getUnreadCount(10L, 20L);

        assertEquals(5L, result);
    }

    @Test
    void getUnreadCount_whenNoConversation_shouldReturnZero() {
        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.empty());

        long result = messageService.getUnreadCount(10L, 20L);

        assertEquals(0L, result);
    }

    @Test
    void addReaction_shouldSaveAndReturn() {
        ReactionRequest request = ReactionRequest.builder()
                .messageId(1L)
                .userId(10L)
                .reactionType(ReactionType.HEART)
                .build();

        MessageReaction reaction = MessageReaction.builder()
                .id(1L)
                .chatMessage(chatMessage)
                .userId(10L)
                .reactionType(ReactionType.HEART)
                .build();

        ReactionResponse reactionResponse = ReactionResponse.builder()
                .reactionId(1L)
                .messageId(1L)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(messageReactionRepository.findByChatMessageAndUserId(chatMessage, 10L))
                .thenReturn(Optional.empty());
        when(messageReactionMapper.toEntity(request)).thenReturn(reaction);
        when(messageReactionRepository.save(any(MessageReaction.class))).thenReturn(reaction);
        when(messageReactionMapper.toResponse(reaction)).thenReturn(reactionResponse);

        ReactionResponse result = messageService.addReaction(request);

        assertNotNull(result);
        verify(messageReactionRepository).save(any(MessageReaction.class));
    }

    @Test
    void addReaction_whenExistingReaction_shouldUpdate() {
        ReactionRequest request = ReactionRequest.builder()
                .messageId(1L)
                .userId(10L)
                .reactionType(ReactionType.THUMBS_UP)
                .build();

        MessageReaction existingReaction = MessageReaction.builder()
                .id(1L)
                .chatMessage(chatMessage)
                .userId(10L)
                .reactionType(ReactionType.HEART)
                .build();

        ReactionResponse reactionResponse = ReactionResponse.builder()
                .reactionId(1L)
                .messageId(1L)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(messageReactionRepository.findByChatMessageAndUserId(chatMessage, 10L))
                .thenReturn(Optional.of(existingReaction));
        when(messageReactionRepository.save(any(MessageReaction.class))).thenReturn(existingReaction);
        when(messageReactionMapper.toResponse(existingReaction)).thenReturn(reactionResponse);

        ReactionResponse result = messageService.addReaction(request);

        assertNotNull(result);
        assertEquals(ReactionType.THUMBS_UP, existingReaction.getReactionType());
    }

    @Test
    void removeReaction_shouldDelete() {
        MessageReaction reaction = MessageReaction.builder()
                .id(1L)
                .chatMessage(chatMessage)
                .build();

        when(messageReactionRepository.findById(1L)).thenReturn(Optional.of(reaction));

        messageService.removeReaction(1L);

        verify(messageReactionRepository).delete(reaction);
    }

    @Test
    void removeReaction_whenNotFound_shouldThrow() {
        when(messageReactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ReactionNotFoundException.class,
                () -> messageService.removeReaction(999L));
    }

    @Test
    void getMessageReactions_shouldReturnList() {
        MessageReaction reaction = MessageReaction.builder()
                .id(1L)
                .chatMessage(chatMessage)
                .build();

        ReactionResponse reactionResponse = ReactionResponse.builder()
                .reactionId(1L)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(messageReactionRepository.findByChatMessage(chatMessage))
                .thenReturn(List.of(reaction));
        when(messageReactionMapper.toResponseList(anyList()))
                .thenReturn(List.of(reactionResponse));

        List<ReactionResponse> result = messageService.getMessageReactions(1L);

        assertEquals(1, result.size());
    }

    @Test
    void exists_whenMessageExists_shouldReturnTrue() {
        when(chatMessageRepository.existsByIdAndDeletedFalse(1L)).thenReturn(true);

        assertTrue(messageService.exists(1L));
    }

    @Test
    void exists_whenMessageNotExists_shouldReturnFalse() {
        when(chatMessageRepository.existsByIdAndDeletedFalse(999L)).thenReturn(false);

        assertFalse(messageService.exists(999L));
    }
}
