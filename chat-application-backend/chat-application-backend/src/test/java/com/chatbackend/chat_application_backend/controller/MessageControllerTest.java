package com.chatbackend.chat_application_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbackend.chat_application_backend.config.TestSecurityConfig;
import com.chatbackend.chat_application_backend.security.CustomUserDetailsService;
import com.chatbackend.chat_application_backend.security.JwtUtil;
import com.chatbackend.chat_application_backend.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(MessageController.class)
@Import(TestSecurityConfig.class)  // Import test security config
public class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageService messageService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void shouldSendMessage() throws Exception {
        mockMvc.perform(post("/api/messages")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "senderId":1,
                          "content":"Hello",
                          "chatRoomId":10
                        }
                        """))
                .andExpect(status().isOk());
    }
}