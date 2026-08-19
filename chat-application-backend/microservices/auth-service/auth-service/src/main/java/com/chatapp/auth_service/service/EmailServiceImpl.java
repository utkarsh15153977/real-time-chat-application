package com.chatapp.auth_service.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendOtp(
            String email,
            String otp
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);

        message.setSubject(
                "Chat Application - Email Verification OTP"
        );

        message.setText(
                "Hello,\n\n" +
                        "Your Chat Application verification OTP is:\n\n" +
                        otp + "\n\n" +
                        "This OTP is valid for 15 minutes.\n\n" +
                        "Please do not share this OTP with anyone.\n\n" +
                        "If you did not create this account, please ignore this email.\n\n" +
                        "Regards,\n" +
                        "Chat Application Team"
        );

        mailSender.send(message);
    }
}
