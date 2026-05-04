package com.chatbackend.chat_application_backend.service.serviceImpl;

import com.chatbackend.chat_application_backend.entity.Message;
import com.chatbackend.chat_application_backend.entity.Reaction;
import com.chatbackend.chat_application_backend.entity.User;
import com.chatbackend.chat_application_backend.exception.MessageNotFoundException;
import com.chatbackend.chat_application_backend.exception.UserNotFoundException;
import com.chatbackend.chat_application_backend.repository.MessageRepository;
import com.chatbackend.chat_application_backend.repository.ReactionRepository;
import com.chatbackend.chat_application_backend.repository.UserRepository;
import com.chatbackend.chat_application_backend.service.ReactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ReactionServiceImpl implements ReactionService {
    private final ReactionRepository reactionRepository;
    private final MessageRepository  messageRepository;
    private final UserRepository userRepository;

    public ReactionServiceImpl(ReactionRepository reactionRepository, MessageRepository messageRepository, UserRepository userRepository) {
        this.reactionRepository = reactionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Reaction addReaction(Long messageId, Long userId, String reactionType){
        log.debug("Adding reaction in one message for user id:{} and message id:{}", userId, messageId);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() ->
                        new MessageNotFoundException("Message not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        reactionRepository.findByMessage_IdAndUser_Id(messageId, userId)
                .ifPresent(r -> {
                    throw new IllegalStateException("User already reacted to this message");
                });

        Reaction reactionToAdd = Reaction.builder()
                .reactionType(reactionType)
                .user(user)
                .message(message)
                .build();

        return reactionRepository.save(reactionToAdd);
    }

    @Override
    public void removeReaction(Long messageId, Long userId){
        log.info("Removing reaction from user {} on message {}", userId, messageId);
        Reaction reaction = reactionRepository.findByMessage_IdAndUser_Id(messageId, userId)
                .orElseThrow(() ->
                        new MessageNotFoundException("Reaction not found"));
        reactionRepository.delete(reaction);
    }

    @Override
    public Reaction updateReaction(Long messageId, Long userId, String reactionType){
        log.debug("Updating reaction from user {} on message {}", userId, messageId);
        Reaction reaction = reactionRepository.findByMessage_IdAndUser_Id(messageId, userId)
                .orElseThrow(() ->
                        new MessageNotFoundException("Reaction not found"));

        reaction.setReactionType(reactionType);
        return reactionRepository.save(reaction);
    }

    @Override
    public List<Reaction> getReactionsByMessage(Long messageId){
        log.debug("Fetching reactions for message {}", messageId);
        return reactionRepository.findByMessage_Id(messageId);
    }

    @Override
    public List<Reaction> getReactionsByUser(Long userId){
        log.debug("Fetching reactions for user {}", userId);
        return reactionRepository.findByUser_Id(userId);
    }

    @Override
    public long countReactionsByMessage(Long messageId){
        log.debug("Counting the reactions for message {}", messageId);
        return reactionRepository.countByMessage_Id(messageId);
    }
}
