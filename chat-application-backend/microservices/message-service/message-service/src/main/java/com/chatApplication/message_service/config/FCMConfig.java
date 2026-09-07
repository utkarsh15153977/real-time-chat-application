package com.chatApplication.message_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;

/**
 * Firebase Cloud Messaging configuration.
 * <p>
 * Initializes the Firebase Admin SDK using either:
 *   1. A service account JSON file path ({@code gcp.firebase.credentials-path})
 *   2. A Base64-encoded service account JSON ({@code gcp.firebase.credentials-base64})
 * <p>
 * If neither is configured, push notifications are silently disabled (dry-run mode)
 * so the application can still run for local development and integration testing.
 */
@Slf4j
@Configuration
public class FCMConfig {

    @Value("${gcp.firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${gcp.firebase.credentials-base64:}")
    private String credentialsBase64;

    private static boolean firebaseEnabled = false;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps() != null && !FirebaseApp.getApps().isEmpty()) {
                firebaseEnabled = true;
                log.info("FirebaseApp already initialized");
                return;
            }

            InputStream serviceAccount = resolveCredentials();
            if (serviceAccount == null) {
                log.warn("Firebase credentials not configured. Push notifications disabled (dry-run mode). "
                        + "Set 'gcp.firebase.credentials-path' or 'gcp.firebase.credentials-base64' to enable.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            firebaseEnabled = true;
            log.info("FirebaseApp initialized successfully - FCM push notifications enabled");

        } catch (IOException e) {
            log.error("Failed to initialize FirebaseApp: {}. Push notifications disabled.", e.getMessage());
            firebaseEnabled = false;
        }
    }

    private InputStream resolveCredentials() throws IOException {
        // Strategy 1: File path
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            Path path = Paths.get(credentialsPath);
            if (Files.exists(path)) {
                log.debug("Loading Firebase credentials from file: {}", credentialsPath);
                return Files.newInputStream(path);
            }
            log.warn("Firebase credentials file not found: {}", credentialsPath);
        }

        // Strategy 2: Base64-encoded environment variable
        if (credentialsBase64 != null && !credentialsBase64.isBlank()) {
            log.debug("Loading Firebase credentials from base64 environment variable");
            byte[] decoded = Base64.getDecoder().decode(credentialsBase64);
            return new ByteArrayInputStream(decoded);
        }

        return null;
    }

    /**
     * Returns whether Firebase has been successfully initialized.
     * Used by PushNotificationService to skip FCM dispatch in dry-run mode.
     */
    public static boolean isFirebaseEnabled() {
        return firebaseEnabled;
    }
}
