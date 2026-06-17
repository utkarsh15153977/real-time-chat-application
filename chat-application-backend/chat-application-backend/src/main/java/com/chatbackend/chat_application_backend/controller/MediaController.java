package com.chatbackend.chat_application_backend.controller;

import com.chatbackend.chat_application_backend.service.FileUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/media")
// Allow your React app (usually on port 3000) to call this API
@CrossOrigin(origins = "http://localhost:3000")
public class MediaController {

    private final FileUploadService fileUploadService;

    // Constructor injection is preferred over @Autowired for better testability
    public MediaController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadMedia(@RequestParam("file") MultipartFile file) {
        try {
            // 1. Upload the file using our service
            String fileUrl = fileUploadService.uploadFile(file);

            // 2. Wrap the URL in a Map to return as JSON: {"url": "https://..."}
            return ResponseEntity.ok(Map.of("url", fileUrl));

        } catch (Exception e) {
            // 3. Return a 500 error if something goes wrong with AWS or the file stream
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}