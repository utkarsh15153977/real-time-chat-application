package com.chatApplication.message_service.config;

/**
 * Utility class for masking sensitive data in log output.
 * <p>
 * Prevents accidental exposure of JWT tokens, secrets, and credentials
 * in application logs, which could be accessed by operations teams or
 * leaked through log aggregation systems.
 * <p>
 * Usage:
 * <pre>
 *   log.info("Token: {}", SecurityLogUtils.maskToken(rawJwt));
 *   // Output: Token: eyJhbGciOi[...MASKED]xYz12
 * </pre>
 */
public final class SecurityLogUtils {

    private SecurityLogUtils() {
        // Utility class - no instantiation
    }

    /**
     * Masks a JWT token for safe logging.
     * <p>
     * Format: First 10 chars + [...MASKED] + last 4 chars
     * Example: eyJhbGciOi[...MASKED]xYz12
     *
     * @param token the raw JWT token
     * @return masked token safe for logging
     */
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

    /**
     * Masks a secret key for safe logging.
     *
     * @param secret the raw secret
     * @return masked secret safe for logging
     */
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

    /**
     * Masks an email address for safe logging.
     *
     * @param email the raw email
     * @return masked email safe for logging
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "[NULL]";
        }

        int atIndex = email.indexOf('@');
        if (atIndex < 2) {
            return "[MASKED]@" + email.substring(atIndex + 1);
        }

        return email.substring(0, 2)
                + "[...MASKED]@"
                + email.substring(atIndex + 1);
    }
}
