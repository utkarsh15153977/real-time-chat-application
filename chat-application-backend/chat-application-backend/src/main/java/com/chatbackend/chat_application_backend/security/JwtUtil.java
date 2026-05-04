package com.chatbackend.chat_application_backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

//Generates JWT token and validate it.
@Component
public class JwtUtil {

    private final String secretKey = "ChatInWhatsAppCloneSuperSecureJwtSecretKey2024";
    private final Long expiration = 1000L * 60 * 60 * 24;

    // Converting secret key to a byte array and then to a SecretKeySpec for signing the JWT
    public Key getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);  // Specify charset for consistency
        return new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
    }

    // JWT token generation method with minor improvement: Remove redundant algorithm in signWith()
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .signWith(getSigningKey())  // Improved: Omit SignatureAlgorithm for JJWT 0.11+
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .compact();
    }

    // Extract Username from the JWT token
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // Extract Claims from the JWT token
    public io.jsonwebtoken.Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Validate token.
//    public boolean validateToken(String token) {
//        String extractedUsername = extractUsername(token);
//        return extractedUsername.equals(username) && !isTokenExpired(token);
//    }
    public boolean validateToken(String token, String username) {
        String extractedUsername = extractUsername(token);
        return extractedUsername.equals(username) && !isTokenExpired(token);
    }
    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }
}
