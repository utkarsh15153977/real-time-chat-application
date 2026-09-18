package com.chatApplication.chat_service.controller;

import com.chatApplication.chat_service.dto.ChatRequest;
import com.chatApplication.chat_service.dto.ChatResponse;
import com.chatApplication.chat_service.dto.GroupRequest;
import com.chatApplication.chat_service.dto.RenameGroupChat;
import com.chatApplication.chat_service.exception.AuthorizationException;
import com.chatApplication.chat_service.exception.ChatNotFoundException;
import com.chatApplication.chat_service.exception.DuplicateMemberException;
import com.chatApplication.chat_service.exception.GlobalExceptionHandler;
import com.chatApplication.chat_service.exception.GroupNotFoundException;
import com.chatApplication.chat_service.exception.MemberNotFoundException;
import com.chatApplication.chat_service.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    private ChatResponse chatResponse;
    private ChatResponse groupResponse;

    @BeforeEach
    void setUp() {
        chatResponse = ChatResponse.builder()
                .chatId(1L)
                .name(null)
                .group(false)
                .groupIcon(null)
                .build();

        groupResponse = ChatResponse.builder()
                .chatId(2L)
                .name("Test Group")
                .group(true)
                .groupIcon("icon.png")
                .build();
    }

    @Nested
    @DisplayName("POST /api/chats/private")
    class CreatePrivateChatTests {

        @Test
        @DisplayName("should create private chat")
        void createPrivateChat_validRequest_returns200() throws Exception {
            ChatRequest request = new ChatRequest();
            request.setSenderId(10L);
            request.setReceiverId(20L);

            when(chatService.createPrivateChat(any(ChatRequest.class))).thenReturn(chatResponse);

            mockMvc.perform(post("/api/chats/private")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chatId").value(1))
                    .andExpect(jsonPath("$.group").value(false));
        }

        @Test
        @DisplayName("should return 400 for missing senderId")
        void createPrivateChat_missingSenderId_returns400() throws Exception {
            ChatRequest request = new ChatRequest();
            request.setReceiverId(20L);

            mockMvc.perform(post("/api/chats/private")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/chats/group")
    class CreateGroupChatTests {

        @Test
        @DisplayName("should create group chat using caller identity, not request body adminId")
        void createGroupChat_validRequest_returns200() throws Exception {
            GroupRequest request = new GroupRequest();
            request.setName("New Group");
            request.setAdminId(99L);
            request.setMembers(List.of(10L, 20L));
            request.setGroupIcon("icon.png");

            when(chatService.createGroupChat(any(GroupRequest.class), eq(10L))).thenReturn(groupResponse);

            mockMvc.perform(post("/api/chats/group")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", "10")
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chatId").value(2))
                    .andExpect(jsonPath("$.group").value(true));

            verify(chatService).createGroupChat(any(GroupRequest.class), eq(10L));
        }

        @Test
        @DisplayName("should return 400 for blank group name")
        void createGroupChat_blankName_returns400() throws Exception {
            GroupRequest request = new GroupRequest();
            request.setName("  ");
            request.setAdminId(10L);
            request.setMembers(List.of(10L));

            mockMvc.perform(post("/api/chats/group")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", "10")
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 403 when X-User-Id header is missing")
        void createGroupChat_missingUserId_returns403() throws Exception {
            GroupRequest request = new GroupRequest();
            request.setName("New Group");
            request.setAdminId(10L);
            request.setMembers(List.of(10L));

            mockMvc.perform(post("/api/chats/group")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/chats/{userId}")
    class GetAllChatsTests {

        @Test
        @DisplayName("should return own chats only")
        void getAllChats_ownUser_returns200() throws Exception {
            when(chatService.getAllChats(10L)).thenReturn(List.of(chatResponse, groupResponse));

            mockMvc.perform(get("/api/chats/{userId}", 10L)
                    .header("X-User-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("should return 403 when trying to view another user's chats")
        void getAllChats_otherUser_returns403() throws Exception {
            mockMvc.perform(get("/api/chats/{userId}", 20L)
                    .header("X-User-Id", "10"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/chats/groups/{groupId}/members")
    class AddMemberTests {

        @Test
        @DisplayName("should add member to group when caller is admin")
        void addMemberToGroup_validRequest_returns200() throws Exception {
            when(chatService.addGroupMember(eq(1L), eq(30L), eq(10L))).thenReturn(groupResponse);

            mockMvc.perform(put("/api/chats/groups/{groupId}/members", 1L)
                    .param("userId", "30")
                    .header("X-User-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chatId").value(2));
        }

        @Test
        @DisplayName("should return 403 when caller is not admin")
        void addMemberToGroup_notAdmin_returns403() throws Exception {
            when(chatService.addGroupMember(eq(1L), eq(30L), eq(20L)))
                    .thenThrow(new AuthorizationException("Only group admins can perform this action"));

            mockMvc.perform(put("/api/chats/groups/{groupId}/members", 1L)
                    .param("userId", "30")
                    .header("X-User-Id", "20"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 409 for duplicate member")
        void addMemberToGroup_duplicateMember_returns409() throws Exception {
            when(chatService.addGroupMember(eq(1L), eq(20L), eq(10L)))
                    .thenThrow(new DuplicateMemberException("User already a member"));

            mockMvc.perform(put("/api/chats/groups/{groupId}/members", 1L)
                    .param("userId", "20")
                    .header("X-User-Id", "10"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should return 403 when X-User-Id header is missing")
        void addMemberToGroup_missingUserId_returns403() throws Exception {
            mockMvc.perform(put("/api/chats/groups/{groupId}/members", 1L)
                    .param("userId", "30"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/chats/{chatId}/rename")
    class RenameGroupTests {

        @Test
        @DisplayName("should rename group when caller is admin")
        void renameGroup_validRequest_returns200() throws Exception {
            RenameGroupChat rename = new RenameGroupChat();
            rename.setName("New Name");

            when(chatService.renameGroup(eq(1L), eq("New Name"), eq(10L))).thenReturn(groupResponse);

            mockMvc.perform(put("/api/chats/{chatId}/rename", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", "10")
                    .content(objectMapper.writeValueAsString(rename)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should return 403 when caller is not admin")
        void renameGroup_notAdmin_returns403() throws Exception {
            RenameGroupChat rename = new RenameGroupChat();
            rename.setName("New Name");

            when(chatService.renameGroup(eq(1L), eq("New Name"), eq(20L)))
                    .thenThrow(new AuthorizationException("Only group admins can perform this action"));

            mockMvc.perform(put("/api/chats/{chatId}/rename", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", "20")
                    .content(objectMapper.writeValueAsString(rename)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 for blank name")
        void renameGroup_blankName_returns400() throws Exception {
            RenameGroupChat rename = new RenameGroupChat();
            rename.setName("  ");

            mockMvc.perform(put("/api/chats/{chatId}/rename", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", "10")
                    .content(objectMapper.writeValueAsString(rename)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/chats/{chatId}/remove")
    class RemoveMemberTests {

        @Test
        @DisplayName("should remove member from group when caller is admin")
        void removeGroupMember_validRequest_returns200() throws Exception {
            mockMvc.perform(delete("/api/chats/{chatId}/remove", 1L)
                    .param("userId", "20")
                    .header("X-User-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Member removed successfully"));
        }

        @Test
        @DisplayName("should return 403 when caller is not admin")
        void removeGroupMember_notAdmin_returns403() throws Exception {
            doThrow(new AuthorizationException("Only group admins can perform this action"))
                    .when(chatService).removeGroupMember(1L, 20L, 20L);

            mockMvc.perform(delete("/api/chats/{chatId}/remove", 1L)
                    .param("userId", "20")
                    .header("X-User-Id", "20"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 when member not found")
        void removeGroupMember_memberNotFound_returns404() throws Exception {
            doThrow(new MemberNotFoundException("Member not found"))
                    .when(chatService).removeGroupMember(1L, 99L, 10L);

            mockMvc.perform(delete("/api/chats/{chatId}/remove", 1L)
                    .param("userId", "99")
                    .header("X-User-Id", "10"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/chats/{chatId}/members")
    class GetMembersTests {

        @Test
        @DisplayName("should return list of member IDs for group member")
        void getGroupMembers_validChat_returns200() throws Exception {
            when(chatService.getGroupMembers(1L, 10L)).thenReturn(List.of(10L, 20L, 30L));

            mockMvc.perform(get("/api/chats/{chatId}/members", 1L)
                    .header("X-User-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0]").value(10));
        }

        @Test
        @DisplayName("should return 403 when caller is not a member")
        void getGroupMembers_notMember_returns403() throws Exception {
            when(chatService.getGroupMembers(1L, 99L))
                    .thenThrow(new AuthorizationException("User is not a member of this group"));

            mockMvc.perform(get("/api/chats/{chatId}/members", 1L)
                    .header("X-User-Id", "99"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/chats/{chatId}/delete")
    class DeleteGroupTests {

        @Test
        @DisplayName("should delete group when caller is creator")
        void deleteGroup_validChat_returns200() throws Exception {
            mockMvc.perform(delete("/api/chats/{chatId}/delete", 1L)
                    .header("X-User-Id", "10"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Group deleted successfully"));
        }

        @Test
        @DisplayName("should return 403 when caller is not creator")
        void deleteGroup_notCreator_returns403() throws Exception {
            doThrow(new AuthorizationException("Only the group creator can perform this action"))
                    .when(chatService).deleteGroup(1L, 20L);

            mockMvc.perform(delete("/api/chats/{chatId}/delete", 1L)
                    .header("X-User-Id", "20"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 404 when chat not found")
        void deleteGroup_chatNotFound_returns404() throws Exception {
            doThrow(new ChatNotFoundException("Chat not found"))
                    .when(chatService).deleteGroup(eq(99L), eq(10L));

            mockMvc.perform(delete("/api/chats/{chatId}/delete", 99L)
                    .header("X-User-Id", "10"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/chats/{chatId}/leave")
    class LeaveGroupTests {

        @Test
        @DisplayName("should leave group using authenticated user identity")
        void leaveGroup_validRequest_returns200() throws Exception {
            mockMvc.perform(delete("/api/chats/{chatId}/leave", 1L)
                    .header("X-User-Id", "20"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Left group successfully"));
        }

        @Test
        @DisplayName("should return 404 when member not in group")
        void leaveGroup_memberNotFound_returns404() throws Exception {
            doThrow(new MemberNotFoundException("Member not found"))
                    .when(chatService).leaveGroup(eq(1L), eq(99L));

            mockMvc.perform(delete("/api/chats/{chatId}/leave", 1L)
                    .header("X-User-Id", "99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 when X-User-Id header is missing")
        void leaveGroup_missingUserId_returns403() throws Exception {
            mockMvc.perform(delete("/api/chats/{chatId}/leave", 1L))
                    .andExpect(status().isForbidden());
        }
    }
}
