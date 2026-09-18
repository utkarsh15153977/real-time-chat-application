package com.chatApplication.chat_service.config;

/**
 * Utility class for masking sensitive data in log output.
 * <p>
 * Prevents accidental exposure of JWT tokens, secrets, and credentials
 * in application logs.
 */
public final class SecurityLogUtils {

    private SecurityLogUtils() {
    }

    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "[NULL]";
        }
        if (token.length() < 16) {
            return "[MASKED]";
        }
        return token.substring(0, 10)
                + "[...MASKED]"
                + token.substring(token.length() - 4);
    }

    public static String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "[NULL]";
        }
        if (secret.length() < 10) {
            return "[MASKED]";
        }
        return secret.substring(0, 4)
                + "[...MASKED]"
                + secret.substring(secret.length() - 4);
    }
}
