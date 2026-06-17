package com.chatapp.auth_service.service;

import com.chatapp.auth_service.dto.AuthResponse;
import com.chatapp.auth_service.dto.LoginRequest;
import com.chatapp.auth_service.dto.RegisterRequest;
import com.chatapp.auth_service.entity.BlacklistedToken;
import com.chatapp.auth_service.entity.User;
import com.chatapp.auth_service.repository.BlacklistedTokenRepository;
import com.chatapp.auth_service.repository.UserRepository;
import com.chatapp.auth_service.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository repository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final BlacklistedTokenRepository blacklistRepository;

    public  AuthServiceImpl(UserRepository repository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder,  BlacklistedTokenRepository blacklistRepository) {
        this.repository = repository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.blacklistRepository = blacklistRepository;
    }

    @Override
    public AuthResponse register(RegisterRequest request){
        if(repository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Email already exists");
        }
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .build();

        repository.save(user);

        String token = jwtUtil.generateToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }

//    @Override
//    public AuthResponse login(LoginRequest request) {
//
//        User user = repository.existsByEmail(request.getEmail())
//                .orElseThrow(() ->
//                        new RuntimeException("User not found"));
//
//        if(!passwordEncoder.matches(
//                request.getPassword(),
//                user.getPassword())) {
//            throw new RuntimeException("Invalid credentials");
//        }
//
//        String token = jwtUtil.generateToken(user);
//
//        return new AuthResponse(
//                token,
//                user.getId(),
//                user.getName(),
//                user.getEmail()
//        );
//    }

    @Override
    public AuthResponse login(LoginRequest request) {

        User user = repository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
    @Override
    public String logout(String token) {

        if(token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        BlacklistedToken blacklisted =
                BlacklistedToken.builder()
                        .token(token)
                        .blacklistedAt(LocalDateTime.now())
                        .build();

        blacklistRepository.save(blacklisted);

        return "Logout successful";
    }
}
