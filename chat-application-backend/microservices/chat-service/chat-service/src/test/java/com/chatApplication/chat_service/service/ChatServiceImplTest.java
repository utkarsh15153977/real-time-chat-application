package com.chatApplication.chat_service.service;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;
import com.chatApplication.chat_service.entity.Chat;
import com.chatApplication.chat_service.entity.ChatMember;
import com.chatApplication.chat_service.exception.ChatNotFoundException;
import com.chatApplication.chat_service.exception.DuplicateMemberException;
import com.chatApplication.chat_service.exception.GroupNotFoundException;
import com.chatApplication.chat_service.exception.MemberNotFoundException;
import com.chatApplication.chat_service.repository.ChatMemberRepository;
import com.chatApplication.chat_service.repository.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class ChatServiceImplTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMemberRepository chatMemberRepository;

    @InjectMocks
    private ChatServiceImpl chatService;

    private Chat groupChat;
    private Chat privateChat;
    private ChatMember member1;
    private ChatMember member2;

    @BeforeEach
    void setUp() {
        groupChat = Chat.builder()
                .id(1L)
                .name("Test Group")
                .isGroup(true)
                .groupIcon("icon.png")
                .createdBy(10L)
                .active(true)
                .build();

        privateChat = Chat.builder()
                .id(2L)
                .name(null)
                .isGroup(false)
                .groupIcon(null)
                .createdBy(10L)
                .active(true)
                .build();

        member1 = ChatMember.builder()
                .id(1L)
                .chatId(1L)
                .userId(10L)
                .admin(true)
                .build();

        member2 = ChatMember.builder()
                .id(2L)
                .chatId(1L)
                .userId(20L)
                .admin(false)
                .build();
    }

    @Nested
    @DisplayName("createPrivateChat")
    class CreatePrivateChatTests {

        @Test
        @DisplayName("should create new private chat when none exists")
        void createPrivateChat_noExistingChat_createsNewChat() {
            ChatRequest request = new ChatRequest();
            request.setSenderId(10L);
            request.setReceiverId(20L);

            when(chatRepository.findPrivateChatBetween(10L, 20L))
                    .thenReturn(Optional.empty());
            when(chatRepository.save(any(Chat.class))).thenReturn(privateChat);
            when(chatMemberRepository.save(any(ChatMember.class))).thenReturn(member1);

            ChatResponse response = chatService.createPrivateChat(request);

            assertNotNull(response);
            assertEquals(2L, response.getChatId());
            assertFalse(response.getGroup());
            verify(chatMemberRepository, times(2)).save(any(ChatMember.class));
        }

        @Test
        @DisplayName("should return existing private chat when one exists")
        void createPrivateChat_existingChat_returnsExistingChat() {
            ChatRequest request = new ChatRequest();
            request.setSenderId(10L);
            request.setReceiverId(20L);

            when(chatRepository.findPrivateChatBetween(10L, 20L))
                    .thenReturn(Optional.of(privateChat));

            ChatResponse response = chatService.createPrivateChat(request);

            assertNotNull(response);
            assertEquals(2L, response.getChatId());
            verify(chatRepository, never()).save(any(Chat.class));
            verify(chatMemberRepository, never()).save(any(ChatMember.class));
        }

        @Test
        @DisplayName("should throw when trying to chat with yourself")
        void createPrivateChat_sameUser_throwsException() {
            ChatRequest request = new ChatRequest();
            request.setSenderId(10L);
            request.setReceiverId(10L);

            assertThrows(IllegalArgumentException.class,
                    () -> chatService.createPrivateChat(request));
        }
    }

    @Nested
    @DisplayName("createGroupChat")
    class CreateGroupChatTests {

        @Test
        @DisplayName("should create group chat with members")
        void createGroupChat_validRequest_createsGroup() {
            GroupRequest request = new GroupRequest();
            request.setName("New Group");
            request.setAdminId(10L);
            request.setMembers(List.of(10L, 20L, 30L));
            request.setGroupIcon("icon.png");

            when(chatRepository.save(any(Chat.class))).thenReturn(groupChat);
            when(chatMemberRepository.save(any(ChatMember.class))).thenReturn(member1);

            ChatResponse response = chatService.createGroupChat(request);

            assertNotNull(response);
            assertEquals(1L, response.getChatId());
            assertTrue(response.getGroup());
            verify(chatMemberRepository, times(3)).save(any(ChatMember.class));
        }

        @Test
        @DisplayName("should throw when group name is blank")
        void createGroupChat_blankName_throwsException() {
            GroupRequest request = new GroupRequest();
            request.setName("  ");
            request.setAdminId(10L);
            request.setMembers(List.of(10L));

            assertThrows(IllegalArgumentException.class,
                    () -> chatService.createGroupChat(request));
        }
    }

    @Nested
    @DisplayName("addGroupMember")
    class AddGroupMemberTests {

        @Test
        @DisplayName("should add new member to group")
        void addGroupMember_newMember_addsSuccessfully() {
            when(chatRepository.findById(1L)).thenReturn(Optional.of(groupChat));
            when(chatMemberRepository.existsByChatIdAndUserId(1L, 30L)).thenReturn(false);
            when(chatMemberRepository.save(any(ChatMember.class))).thenReturn(member2);

            ChatResponse response = chatService.addGroupMember(1L, 30L);

            assertNotNull(response);
            verify(chatMemberRepository).save(any(ChatMember.class));
        }

        @Test
        @DisplayName("should throw DuplicateMemberException when member exists")
        void addGroupMember_existingMember_throwsException() {
            when(chatRepository.findById(1L)).thenReturn(Optional.of(groupChat));
            when(chatMemberRepository.existsByChatIdAndUserId(1L, 20L)).thenReturn(true);

            assertThrows(DuplicateMemberException.class,
                    () -> chatService.addGroupMember(1L, 20L));
            verify(chatMemberRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when chat is not a group")
        void addGroupMember_privateChat_throwsException() {
            when(chatRepository.findById(2L)).thenReturn(Optional.of(privateChat));

            assertThrows(GroupNotFoundException.class,
                    () -> chatService.addGroupMember(2L, 20L));
        }

        @Test
        @DisplayName("should throw when chat not found")
        void addGroupMember_chatNotFound_throwsException() {
            when(chatRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ChatNotFoundException.class,
                    () -> chatService.addGroupMember(99L, 20L));
        }
    }

    @Nested
    @DisplayName("removeGroupMember")
    class RemoveGroupMemberTests {

        @Test
        @DisplayName("should remove existing member")
        void removeGroupMember_existingMember_removes() {
            when(chatMemberRepository.findByChatIdAndUserId(1L, 20L))
                    .thenReturn(Optional.of(member2));

            chatService.removeGroupMember(1L, 20L);

            verify(chatMemberRepository).delete(member2);
        }

        @Test
        @DisplayName("should throw when member not found")
        void removeGroupMember_memberNotFound_throwsException() {
            when(chatMemberRepository.findByChatIdAndUserId(1L, 99L))
                    .thenReturn(Optional.empty());

            assertThrows(MemberNotFoundException.class,
                    () -> chatService.removeGroupMember(1L, 99L));
        }
    }

    @Nested
    @DisplayName("leaveGroup")
    class LeaveGroupTests {

        @Test
        @DisplayName("should allow member to leave group")
        void leaveGroup_existingMember_leaves() {
            when(chatMemberRepository.findByChatIdAndUserId(1L, 20L))
                    .thenReturn(Optional.of(member2));

            chatService.leaveGroup(1L, 20L);

            verify(chatMemberRepository).delete(member2);
        }

        @Test
        @DisplayName("should throw when member not in group")
        void leaveGroup_memberNotFound_throwsException() {
            when(chatMemberRepository.findByChatIdAndUserId(1L, 99L))
                    .thenReturn(Optional.empty());

            assertThrows(MemberNotFoundException.class,
                    () -> chatService.leaveGroup(1L, 99L));
        }
    }

    @Nested
    @DisplayName("renameGroup")
    class RenameGroupTests {

        @Test
        @DisplayName("should rename group")
        void renameGroup_validName_renames() {
            when(chatRepository.findById(1L)).thenReturn(Optional.of(groupChat));
            when(chatRepository.save(any(Chat.class))).thenReturn(groupChat);

            ChatResponse response = chatService.renameGroup(1L, "New Name");

            assertNotNull(response);
            assertEquals("New Name", groupChat.getName());
        }

        @Test
        @DisplayName("should throw for blank name")
        void renameGroup_blankName_throwsException() {
            when(chatRepository.findById(1L)).thenReturn(Optional.of(groupChat));

            assertThrows(IllegalArgumentException.class,
                    () -> chatService.renameGroup(1L, "  "));
        }

        @Test
        @DisplayName("should throw when chat is not a group")
        void renameGroup_privateChat_throwsException() {
            when(chatRepository.findById(2L)).thenReturn(Optional.of(privateChat));

            assertThrows(GroupNotFoundException.class,
                    () -> chatService.renameGroup(2L, "New Name"));
        }
    }

    @Nested
    @DisplayName("getGroupMembers")
    class GetGroupMembersTests {

        @Test
        @DisplayName("should return list of member IDs")
        void getGroupMembers_validGroup_returnsList() {
            when(chatRepository.findById(1L)).thenReturn(Optional.of(groupChat));
            when(chatMemberRepository.findByChatId(1L)).thenReturn(List.of(member1, member2));

            List<Long> members = chatService.getGroupMembers(1L);

            assertEquals(2, members.size());
            assertTrue(members.contains(10L));
            assertTrue(members.contains(20L));
        }

        @Test
        @DisplayName("should throw when chat is not a group")
        void getGroupMembers_privateChat_throwsException() {
            when(chatRepository.findById(2L)).thenReturn(Optional.of(privateChat));

            assertThrows(GroupNotFoundException.class,
                    () -> chatService.getGroupMembers(2L));
        }
    }

    @Nested
    @DisplayName("deleteGroup")
    class DeleteGroupTests {

        @Test
        @DisplayName("should delete group and all members")
        void deleteGroup_validGroup_deletes() {
            when(chatRepository.findById(1L)).thenReturn(Optional.of(groupChat));
            when(chatMemberRepository.findByChatId(1L)).thenReturn(List.of(member1, member2));

            chatService.deleteGroup(1L);

            verify(chatMemberRepository).deleteAll(List.of(member1, member2));
            verify(chatRepository).delete(groupChat);
        }

        @Test
        @DisplayName("should throw when chat is not a group")
        void deleteGroup_privateChat_throwsException() {
            when(chatRepository.findById(2L)).thenReturn(Optional.of(privateChat));

            assertThrows(GroupNotFoundException.class,
                    () -> chatService.deleteGroup(2L));
        }
    }
}
