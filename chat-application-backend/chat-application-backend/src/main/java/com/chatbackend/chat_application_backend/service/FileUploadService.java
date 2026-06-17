package com.chatbackend.chat_application_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileUploadService {

    public String uploadFile(MultipartFile file) {
        // Dummy logic (replace with AWS S3 or storage logic)
        return "http://localhost:8080/uploads/" + file.getOriginalFilename();
    }
}