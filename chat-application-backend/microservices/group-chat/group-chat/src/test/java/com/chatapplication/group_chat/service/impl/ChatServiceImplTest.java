package com.chatapplication.group_chat.service.impl;

import com.chatapplication.group_chat.dto.request.ChatMessageRequest;
import com.chatapplication.group_chat.dto.request.DeleteMessageRequest;
import com.chatapplication.group_chat.dto.request.EditMessageRequest;
import com.chatapplication.group_chat.dto.request.ReactionRequest;
import com.chatapplication.group_chat.dto.response.ChatMessageResponse;
import com.chatapplication.group_chat.dto.response.ConversationResponse;
import com.chatapplication.group_chat.dto.response.ReactionResponse;
import com.chatapplication.group_chat.entitty.*;
import com.chatapplication.group_chat.exception.*;
import com.chatapplication.group_chat.mapper.ChatMessageMapper;
import com.chatapplication.group_chat.mapper.ConversationMapper;
import com.chatapplication.group_chat.mapper.MessageReactionMapper;
import com.chatapplication.group_chat.repository.*;
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
class ChatServiceImplTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageReactionRepository messageReactionRepository;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private ConversationMapper conversationMapper;
    @Mock private MessageReactionMapper messageReactionMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatServiceImpl chatService;

    private Conversation conversation;
    private ChatMessage chatMessage;
    private ChatMessageResponse chatMessageResponse;

    @BeforeEach
    void setUp() {
        conversation = Conversation.builder()
                .id(1L)
                .user1Id(10L)
                .user2Id(20L)
                .user1UnreadCount(0)
                .user2UnreadCount(0)
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

        chatMessageResponse = ChatMessageResponse.builder()
                .messageId(1L)
                .conversationId(1L)
                .senderId(10L)
                .receiverId(20L)
                .content("Hello")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .build();
    }

    @Test
    void sendMessage_shouldSaveAndReturnResponse() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(10L)
                .receiverId(20L)
                .content("Hello")
                .messageType(MessageType.TEXT)
                .build();

        ChatMessage newMessage = ChatMessage.builder()
                .senderId(10L)
                .receiverId(20L)
                .content("Hello")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .build();

        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.of(conversation));
        when(chatMessageMapper.toEntity(any(ChatMessageRequest.class))).thenReturn(newMessage);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(chatMessageResponse);

        ChatMessageResponse result = chatService.sendMessage(request);

        assertNotNull(result);
        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(conversationRepository, atLeastOnce()).save(any(Conversation.class));
    }

    @Test
    void sendMessage_whenNewConversation_shouldCreate() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(10L)
                .receiverId(20L)
                .content("Hello")
                .build();

        ChatMessage newMessage = ChatMessage.builder()
                .senderId(10L)
                .receiverId(20L)
                .content("Hello")
                .messageType(MessageType.TEXT)
                .status(MessageStatus.SENT)
                .build();

        when(conversationRepository.findConversation(10L, 20L))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
        when(chatMessageMapper.toEntity(any(ChatMessageRequest.class))).thenReturn(newMessage);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(chatMessageResponse);

        ChatMessageResponse result = chatService.sendMessage(request);

        assertNotNull(result);
        verify(conversationRepository, atLeastOnce()).save(any(Conversation.class));
    }

    @Test
    void sendMessage_whenSenderIdNull_shouldThrow() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(null)
                .receiverId(20L)
                .content("Hello")
                .build();

        assertThrows(InvalidMessageException.class, () -> chatService.sendMessage(request));
    }

    @Test
    void sendMessage_whenReceiverIdNull_shouldThrow() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(10L)
                .receiverId(null)
                .content("Hello")
                .build();

        assertThrows(InvalidMessageException.class, () -> chatService.sendMessage(request));
    }

    @Test
    void sendMessage_whenContentEmpty_shouldThrow() {
        ChatMessageRequest request = ChatMessageRequest.builder()
                .senderId(10L)
                .receiverId(20L)
                .content("")
                .build();

        assertThrows(InvalidMessageException.class, () -> chatService.sendMessage(request));
    }

    @Test
    void editMessage_shouldUpdateAndReturn() {
        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(1L)
                .userId(10L)
                .content("Updated content")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(chatMessageResponse);

        ChatMessageResponse result = chatService.editMessage(request);

        assertNotNull(result);
        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void editMessage_whenNotOwner_shouldThrow() {
        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(1L)
                .userId(999L)
                .content("Updated")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(UnauthorizedException.class, () -> chatService.editMessage(request));
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

        assertThrows(MessageAlreadyDeletedException.class, () -> chatService.editMessage(request));
    }

    @Test
    void editMessage_whenAlreadyEdited_shouldThrow() {
        chatMessage.setEdited(true);
        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(1L)
                .userId(10L)
                .content("Updated")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(MessageAlreadyEditedException.class, () -> chatService.editMessage(request));
    }

    @Test
    void editMessage_whenContentEmpty_shouldThrow() {
        EditMessageRequest request = EditMessageRequest.builder()
                .messageId(1L)
                .userId(10L)
                .content("")
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(InvalidMessageException.class, () -> chatService.editMessage(request));
    }

    @Test
    void deleteMessage_shouldDelete() {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(1L)
                .userId(10L)
                .deleteForEveryone(false)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);

        chatService.deleteMessage(request);

        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void deleteMessage_whenNotOwner_shouldThrow() {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(1L)
                .userId(999L)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        assertThrows(RuntimeException.class, () -> chatService.deleteMessage(request));
    }

    @Test
    void deleteMessage_whenAlreadyDeleted_shouldReturnSilently() {
        chatMessage.setDeleted(true);
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(1L)
                .userId(10L)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        chatService.deleteMessage(request);

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void deleteMessage_whenNotFound_shouldThrow() {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .messageId(999L)
                .userId(10L)
                .build();

        when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(MessageNotFoundException.class, () -> chatService.deleteMessage(request));
    }

    @Test
    void getMessage_shouldReturnMessage() {
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageMapper.toResponse(chatMessage)).thenReturn(chatMessageResponse);

        ChatMessageResponse result = chatService.getMessage(1L);

        assertNotNull(result);
        assertEquals(1L, result.getMessageId());
    }

    @Test
    void getMessage_whenNotFound_shouldThrow() {
        when(chatMessageRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(MessageNotFoundException.class, () -> chatService.getMessage(999L));
    }

    @Test
    void getConversation_shouldReturnMessages() {
        when(chatMessageRepository.findConversation(10L, 20L))
                .thenReturn(List.of(chatMessage));
        when(chatMessageMapper.toResponseList(anyList()))
                .thenReturn(List.of(chatMessageResponse));

        List<ChatMessageResponse> result = chatService.getConversation(10L, 20L);

        assertEquals(1, result.size());
    }

    @Test
    void getUserConversations_shouldReturnList() {
        when(conversationRepository.findBySenderIdOrReceiverId(10L, 10L))
                .thenReturn(List.of(conversation));
        when(conversationMapper.toResponseList(anyList()))
                .thenReturn(List.of(ConversationResponse.builder()
                        .conversationId(1L)
                        .userId(10L)
                        .build()));

        List<ConversationResponse> result = chatService.getUserConversations(10L);

        assertEquals(1, result.size());
    }

    @Test
    void getUnreadCount_shouldReturnCount() {
        when(chatMessageRepository.countUnreadMessages(10L, 20L))
                .thenReturn(5L);

        Long result = chatService.getUnreadCount(10L, 20L);

        assertEquals(5L, result);
    }

    @Test
    void markDelivered_shouldUpdateStatus() {
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(chatMessageResponse);

        chatService.markDelivered(1L);

        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void markDelivered_whenAlreadyDelivered_shouldNotSave() {
        chatMessage.setStatus(MessageStatus.DELIVERED);
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        chatService.markDelivered(1L);

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void markSeen_shouldUpdateStatus() {
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(chatMessage);
        when(chatMessageMapper.toResponse(any(ChatMessage.class))).thenReturn(chatMessageResponse);

        chatService.markSeen(1L);

        verify(chatMessageRepository).save(any(ChatMessage.class));
    }

    @Test
    void markSeen_whenAlreadySeen_shouldNotSave() {
        chatMessage.setStatus(MessageStatus.SEEN);
        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));

        chatService.markSeen(1L);

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void markAllSeen_shouldMarkAllUnread() {
        ChatMessage unreadMsg = ChatMessage.builder()
                .id(2L)
                .conversation(conversation)
                .senderId(10L)
                .receiverId(20L)
                .status(MessageStatus.SENT)
                .build();

        when(chatMessageRepository.findUnreadMessages(10L, 20L))
                .thenReturn(List.of(unreadMsg));
        when(chatMessageRepository.saveAll(anyList())).thenReturn(List.of(unreadMsg));

        chatService.markAllSeen(10L, 20L);

        verify(chatMessageRepository).saveAll(anyList());
        assertEquals(MessageStatus.SEEN, unreadMsg.getStatus());
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
                .userId(10L)
                .reactionType(ReactionType.HEART)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(messageReactionRepository.findByChatMessageAndUserId(chatMessage, 10L))
                .thenReturn(Optional.empty());
        when(messageReactionMapper.toEntity(request)).thenReturn(reaction);
        when(messageReactionRepository.save(any(MessageReaction.class))).thenReturn(reaction);
        when(messageReactionMapper.toResponse(reaction)).thenReturn(reactionResponse);

        ReactionResponse result = chatService.addReaction(request);

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
                .userId(10L)
                .reactionType(ReactionType.THUMBS_UP)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(messageReactionRepository.findByChatMessageAndUserId(chatMessage, 10L))
                .thenReturn(Optional.of(existingReaction));
        when(messageReactionRepository.save(any(MessageReaction.class))).thenReturn(existingReaction);
        when(messageReactionMapper.toResponse(existingReaction)).thenReturn(reactionResponse);

        ReactionResponse result = chatService.addReaction(request);

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

        chatService.removeReaction(1L);

        verify(messageReactionRepository).delete(reaction);
    }

    @Test
    void removeReaction_whenNotFound_shouldThrow() {
        when(messageReactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ReactionNotFoundException.class,
                () -> chatService.removeReaction(999L));
    }

    @Test
    void getMessageReactions_shouldReturnList() {
        MessageReaction reaction = MessageReaction.builder()
                .id(1L)
                .chatMessage(chatMessage)
                .userId(10L)
                .build();

        ReactionResponse reactionResponse = ReactionResponse.builder()
                .reactionId(1L)
                .messageId(1L)
                .userId(10L)
                .build();

        when(chatMessageRepository.findById(1L)).thenReturn(Optional.of(chatMessage));
        when(messageReactionRepository.findByChatMessage(chatMessage))
                .thenReturn(List.of(reaction));
        when(messageReactionMapper.toResponseList(anyList()))
                .thenReturn(List.of(reactionResponse));

        List<ReactionResponse> result = chatService.getMessageReactions(1L);

        assertEquals(1, result.size());
    }
}
