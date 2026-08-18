package com.chatapplication.group_chat.service.impl;

import com.chatapplication.group_chat.dto.response.ConversationResponse;
import com.chatapplication.group_chat.entitty.Conversation;
import com.chatapplication.group_chat.exception.ConversationNotFoundException;
import com.chatapplication.group_chat.mapper.ConversationMapper;
import com.chatapplication.group_chat.repository.ConversationRepository;
import com.chatapplication.group_chat.service.ConversationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMapper conversationMapper;

    /**
     * Find conversation by id.
     */
    private Conversation getConversationOrThrow(Long conversationId) {

        return conversationRepository.findById(conversationId)
                .orElseThrow(() ->
                        new ConversationNotFoundException(
                                "Conversation not found with id: " + conversationId
                        ));
    }

    @Override
    public ConversationResponse createConversation(
            Long senderId,
            Long receiverId) {

        log.info("Creating conversation between {} and {}",
                senderId,
                receiverId);

        Conversation conversation =
                conversationRepository.findConversation(
                        senderId,
                        receiverId
                ).orElseGet(() -> {

                    Conversation newConversation =
                            Conversation.builder()
                                    .user1Id(senderId)
                                    .user2Id(receiverId)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .archived(false)
                                    .deleted(false)
                                    .build();

                    return conversationRepository.save(newConversation);
                });

        return conversationMapper.toResponse(conversation);
    }

    @Override
    @Transactional
    public ConversationResponse getConversation(Long conversationId) {

        log.info("Fetching conversation {}", conversationId);

        return conversationMapper.toResponse(
                getConversationOrThrow(conversationId)
        );
    }

    @Override
    @Transactional
    public ConversationResponse getConversationBetweenUsers(
            Long senderId,
            Long receiverId) {

        log.info("Fetching conversation between {} and {}",
                senderId,
                receiverId);

        Conversation conversation =
                conversationRepository.findConversation(
                        senderId,
                        receiverId
                ).orElseThrow(() ->
                        new ConversationNotFoundException(
                                "Conversation not found."
                        ));

        return conversationMapper.toResponse(conversation);
    }

    @Override
    @Transactional
    public List<ConversationResponse> getUserConversations(
            Long userId) {

        log.info("Fetching conversations of user {}",
                userId);

        return conversationMapper.toResponseList(
                conversationRepository.findBySenderIdOrReceiverId(
                        userId,
                        userId
                )
        );
    }

    @Override
    public void archiveConversation(Long conversationId) {

        Conversation conversation =
                getConversationOrThrow(conversationId);

        conversation.setArchived(true);
        conversation.setUpdatedAt(LocalDateTime.now());

        conversationRepository.save(conversation);

        log.info("Conversation {} archived.",
                conversationId);
    }

    @Override
    public void unarchiveConversation(Long conversationId) {

        Conversation conversation =
                getConversationOrThrow(conversationId);

        conversation.setArchived(false);
        conversation.setUpdatedAt(LocalDateTime.now());

        conversationRepository.save(conversation);

        log.info("Conversation {} unarchived.",
                conversationId);
    }

    @Override
    public void deleteConversation(Long conversationId) {

        Conversation conversation =
                getConversationOrThrow(conversationId);

        conversation.setDeleted(true);
        conversation.setUpdatedAt(LocalDateTime.now());

        conversationRepository.save(conversation);

        log.info("Conversation {} deleted.",
                conversationId);
    }

    @Override
    public void restoreConversation(Long conversationId) {

        Conversation conversation =
                getConversationOrThrow(conversationId);

        conversation.setDeleted(false);
        conversation.setUpdatedAt(LocalDateTime.now());

        conversationRepository.save(conversation);

        log.info("Conversation {} restored.",
                conversationId);
    }

    @Override
    @Transactional
    public boolean exists(Long conversationId) {

        return conversationRepository.existsById(
                conversationId
        );
    }

    @Override
    @Transactional
    public long countUserConversations(Long userId) {

        return conversationRepository
                .findBySenderIdOrReceiverId(
                        userId,
                        userId
                )
                .size();
    }
}