package com.chatapp.auth_service.service;

import com.chatapp.auth_service.dto.AuthResponse;
import com.chatapp.auth_service.dto.LoginRequest;
import com.chatapp.auth_service.dto.RegisterRequest;
import com.chatapp.auth_service.entity.BlacklistedToken;
import com.chatapp.auth_service.entity.OtpPurpose;
import com.chatapp.auth_service.entity.User;
import com.chatapp.auth_service.exception.EmailAlreadyExistsException;
import com.chatapp.auth_service.exception.InvalidCredentialsException;
import com.chatapp.auth_service.exception.UserNotFoundException;
import com.chatapp.auth_service.repository.BlacklistedTokenRepository;
import com.chatapp.auth_service.repository.UserRepository;
import com.chatapp.auth_service.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final BlacklistedTokenRepository blacklistRepository;
    private final OtpService otpService;

    public AuthServiceImpl(
            UserRepository userRepository,
            JwtUtil jwtUtil,
            PasswordEncoder passwordEncoder,
            BlacklistedTokenRepository blacklistRepository,
            OtpService otpService) {

        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.blacklistRepository = blacklistRepository;
        this.otpService = otpService;
    }

    // =========================================================
    // REGISTER
    // =========================================================

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        log.info(
                "Attempting to register user with email: {}",
                request.getEmail()
        );

        if (userRepository.existsByEmail(request.getEmail())) {

            log.warn(
                    "Registration failed: Email {} already exists",
                    request.getEmail()
            );

            throw new EmailAlreadyExistsException(
                    "Email already registered: " + request.getEmail()
            );
        }

        if (!isValidPassword(request.getPassword())) {

            log.warn(
                    "Registration failed: Weak password for email {}",
                    request.getEmail()
            );

            throw new IllegalArgumentException(
                    "Password must be at least 8 characters with uppercase, " +
                            "lowercase, digit, and special character"
            );
        }

        try {

            User user = User.builder()
                    .name(request.getName())
                    .email(request.getEmail())
                    .password(
                            passwordEncoder.encode(request.getPassword())
                    )
                    .phone(request.getPhone())
                    .createdAt(LocalDateTime.now())
                    .online(false)
                    .twoFactorEnabled(false)
                    .emailVerified(false)
                    .phoneVerified(false)
                    .build();

            User savedUser = userRepository.save(user);

            log.info(
                    "User created successfully with ID: {}",
                    savedUser.getId()
            );

            // Registration OTP
            //otpService.generateOtp(savedUser.getId());
            otpService.generateOtp(
                    savedUser.getId(),
                    OtpPurpose.REGISTRATION
            );
            log.info(
                    "Registration OTP generated for user ID: {}",
                    savedUser.getId()
            );

            return AuthResponse.builder()
                    .token(null)
                    .userId(savedUser.getId())
                    .name(savedUser.getName())
                    .email(savedUser.getEmail())
                    .message(
                            "Registration successful. " +
                                    "Please verify the OTP sent to your email and mobile."
                    )
                    .requiresTwoFactor(true)
                    .build();

        } catch (EmailAlreadyExistsException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Error during registration for email: {}",
                    request.getEmail(),
                    e
            );

            throw new RuntimeException(
                    "Registration failed: " + e.getMessage()
            );
        }
    }

    // =========================================================
    // VERIFY REGISTRATION OTP
    // =========================================================

    @Override
    @Transactional
    public AuthResponse verifyRegistrationOtp(
            Long userId,
            String otp) {

        log.info(
                "Verifying registration OTP for user ID: {}",
                userId
        );

        try {

            User user = userRepository
                    .findById(userId)
                    .orElseThrow(() ->
                            new UserNotFoundException(
                                    "User not found with ID: " + userId
                            )
                    );

            if (Boolean.TRUE.equals(user.getEmailVerified())
                    && Boolean.TRUE.equals(user.getPhoneVerified())) {

                return AuthResponse.builder()
                        .token(null)
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .message("User is already verified")
                        .requiresTwoFactor(false)
                        .build();
            }

            boolean valid = otpService.verifyOtp(
                    userId,
                    otp,
                    OtpPurpose.REGISTRATION
            );

            if (!valid) {

                log.warn(
                        "Invalid or expired registration OTP for user ID: {}",
                        userId
                );

                throw new InvalidCredentialsException(
                        "Invalid or expired OTP"
                );
            }

            user.setEmailVerified(true);
            user.setPhoneVerified(true);

            userRepository.save(user);

            log.info(
                    "Registration verification successful for user ID: {}",
                    userId
            );

            return AuthResponse.builder()
                    .token(null)
                    .userId(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .message(
                            "Registration verified successfully. " +
                                    "You can now login."
                    )
                    .requiresTwoFactor(false)
                    .build();

        } catch (UserNotFoundException |
                 InvalidCredentialsException |
                 IllegalStateException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Unexpected error during OTP verification " +
                            "for user ID: {}",
                    userId,
                    e
            );

            throw new RuntimeException(
                    "OTP verification failed: " + e.getMessage()
            );
        }
    }

    // =========================================================
    // LOGIN
    // =========================================================

    @Override
    public AuthResponse login(LoginRequest request) {

        log.info(
                "Attempting login for email: {}",
                request.getEmail()
        );

        try {

            // -------------------------------------------------
            // Find user
            // -------------------------------------------------

            User user = userRepository
                    .findByEmail(request.getEmail())
                    .orElseThrow(() -> {

                        log.warn(
                                "Login failed: User not found with email: {}",
                                request.getEmail()
                        );

                        return new UserNotFoundException(
                                "User not found with email: "
                                        + request.getEmail()
                        );
                    });

            // -------------------------------------------------
            // Check registration verification
            // -------------------------------------------------

            if (!Boolean.TRUE.equals(user.getEmailVerified())
                    || !Boolean.TRUE.equals(user.getPhoneVerified())) {

                log.warn(
                        "Login rejected: User {} is not verified",
                        user.getEmail()
                );

                throw new InvalidCredentialsException(
                        "Please verify your email and phone number first"
                );
            }

            // -------------------------------------------------
            // Verify password
            // -------------------------------------------------

            if (!passwordEncoder.matches(
                    request.getPassword(),
                    user.getPassword())) {

                log.warn(
                        "Login failed: Invalid password for email: {}",
                        request.getEmail()
                );

                throw new InvalidCredentialsException(
                        "Invalid email or password"
                );
            }

            // -------------------------------------------------
            // 2FA CHECK
            // -------------------------------------------------

            if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {

                log.info(
                        "2FA enabled for user: {}",
                        user.getEmail()
                );

                // Generate login OTP
                otpService.generateOtp(
                        user.getId(),
                        OtpPurpose.LOGIN_2FA
                );

                log.info(
                        "Login 2FA OTP generated for user ID: {}",
                        user.getId()
                );

                // IMPORTANT:
                // Do not generate JWT yet.
                return AuthResponse.builder()
                        .token(null)
                        .userId(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .message("OTP required for login")
                        .requiresTwoFactor(true)
                        .build();
            }

            // -------------------------------------------------
            // 2FA DISABLED → DIRECT LOGIN
            // -------------------------------------------------

            user.setLastLogin(LocalDateTime.now());

            userRepository.save(user);

            String token = jwtUtil.generateToken(user);

            log.info(
                    "Login successful for user: {}",
                    user.getEmail()
            );

            return AuthResponse.builder()
                    .token(token)
                    .userId(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .message("Login successful")
                    .requiresTwoFactor(false)
                    .build();

        } catch (UserNotFoundException |
                 InvalidCredentialsException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Unexpected error during login for email: {}",
                    request.getEmail(),
                    e
            );

            throw new RuntimeException(
                    "Login failed: " + e.getMessage()
            );
        }
    }

    // =========================================================
    // VERIFY LOGIN 2FA OTP
    // =========================================================

//    @Override
//    @Transactional
//    public AuthResponse verifyLoginOtp(
//            String email,
//            String otp) {
//
//        log.info(
//                "Verifying login 2FA OTP for email: {}",
//                email
//        );
//        try {
//            // -------------------------------------------------
//            // Find user
//            // -------------------------------------------------
//            User user = userRepository
//                    .findByEmail(email)
//                    .orElseThrow(() ->
//                            new UserNotFoundException(
//                                    "User not found with email: " + email
//                            )
//                    );
//            // -------------------------------------------------
//            // Check whether 2FA is enabled
//            // -------------------------------------------------
//            if (!Boolean.TRUE.equals(
//                    user.getTwoFactorEnabled())) {
//                throw new InvalidCredentialsException(
//                        "Two-factor authentication is not enabled"
//                );
//            }
//            // -------------------------------------------------
//            // Verify OTP
//            // -------------------------------------------------
//            boolean valid = otpService.verifyOtp(
//                    user.getId(),
//                    otp,
//                    OtpPurpose.LOGIN_2FA
//            );
//            if (!valid) {
//                log.warn(
//                        "Invalid or expired login OTP for user: {}",
//                        email
//                );
//                throw new InvalidCredentialsException(
//                        "Invalid or expired OTP"
//                );
//            }
//            // -------------------------------------------------
//            // Update last login
//            // -------------------------------------------------
//            user.setLastLogin(LocalDateTime.now());
//            userRepository.save(user);
//            // -------------------------------------------------
//            // Generate JWT ONLY after successful OTP
//            // -------------------------------------------------
//            String token = jwtUtil.generateToken(user);
//            log.info(
//                    "2FA login successful for user: {}",
//                    user.getEmail()
//            );
//            return AuthResponse.builder()
//                    .token(token)
//                    .userId(user.getId())
//                    .name(user.getName())
//                    .email(user.getEmail())
//                    .message("Login successful")
//                    .requiresTwoFactor(false)
//                    .build();
//        } catch (UserNotFoundException |
//                 InvalidCredentialsException e) {
//            throw e;
//        } catch (Exception e) {
//            log.error(
//                    "Unexpected error during login OTP verification " +
//                            "for email: {}",
//                    email,
//                    e
//            );
//            throw new RuntimeException(
//                    "Login OTP verification failed: " + e.getMessage()
//            );
//        }
//    }
@Override
@Transactional
public AuthResponse verifyLoginOtp(
        String email,
        String otp) {

    log.info(
            "Verifying login 2FA OTP for email: {}",
            email
    );

    try {

        // -------------------------------------------------
        // Validate input
        // -------------------------------------------------

        if (email == null || email.isBlank()) {
            throw new InvalidCredentialsException(
                    "Email is required"
            );
        }

        if (otp == null || !otp.matches("\\d{6}")) {
            throw new InvalidCredentialsException(
                    "OTP must be 6 digits"
            );
        }

        // -------------------------------------------------
        // Find user
        // -------------------------------------------------

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User not found with email: " + email
                        )
                );

        // -------------------------------------------------
        // Check whether 2FA is enabled
        // -------------------------------------------------

        if (!Boolean.TRUE.equals(
                user.getTwoFactorEnabled())) {

            throw new InvalidCredentialsException(
                    "Two-factor authentication is not enabled"
            );
        }

        // -------------------------------------------------
        // Verify LOGIN 2FA OTP
        // -------------------------------------------------

        boolean valid = otpService.verifyOtp(
                user.getId(),
                otp,
                OtpPurpose.LOGIN_2FA
        );

        if (!valid) {

            log.warn(
                    "Invalid or expired login OTP for user: {}",
                    email
            );

            throw new InvalidCredentialsException(
                    "Invalid or expired OTP"
            );
        }

        // -------------------------------------------------
        // Update last login
        // -------------------------------------------------

        user.setLastLogin(LocalDateTime.now());

        userRepository.save(user);

        // -------------------------------------------------
        // Generate JWT ONLY after successful OTP
        // -------------------------------------------------

        String token = jwtUtil.generateToken(user);

        log.info(
                "2FA login successful for user: {}",
                user.getEmail()
        );

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .message("Login successful")
                .requiresTwoFactor(false)
                .build();

    } catch (UserNotFoundException |
             InvalidCredentialsException e) {

        throw e;

    } catch (Exception e) {

        log.error(
                "Unexpected error during login OTP verification " +
                        "for email: {}",
                email,
                e
        );

        throw new RuntimeException(
                "Login OTP verification failed",
                e
        );
    }
}
    // =========================================================
    // LOGOUT
    // =========================================================

    @Override
    @Transactional
    public String logout(String token) {
        log.info("Processing logout request");
        try {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException(
                        "Token cannot be empty"
                );
            }
            // Remove Bearer prefix
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            // Validate token
            if (!jwtUtil.validateToken(token)) {
                log.warn(
                        "Logout failed: Invalid or expired token"
                );
                throw new IllegalArgumentException(
                        "Invalid or expired token"
                );
            }
            // Check whether token is already blacklisted
            if (blacklistRepository
                    .findByToken(token)
                    .isPresent()) {

                log.warn(
                        "Logout requested for already blacklisted token"
                );
                return "Already logged out";
            }

            // Create blacklist entry
            BlacklistedToken blacklistedToken =
                    BlacklistedToken.builder()
                            .token(token)
                            .blacklistedAt(LocalDateTime.now())
                            .build();
            blacklistRepository.save(
                    blacklistedToken
            );
            log.info(
                    "JWT token blacklisted successfully"
            );
            return "Logout successful";
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Logout validation failed: {}",
                    e.getMessage()
            );
            throw e;
        } catch (Exception e) {
            log.error(
                    "Unexpected error during logout",
                    e
            );
            throw new RuntimeException(
                    "Logout failed: " + e.getMessage()
            );
        }
    }
    // =========================================================
    // PASSWORD VALIDATION
    // =========================================================
    private boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        String specialChars =
                "!@#$%^&*()_+-=[]{}|;:,.<>?";
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            }
            if (Character.isLowerCase(c)) {
                hasLower = true;
            }
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (specialChars.indexOf(c) >= 0) {
                hasSpecial = true;
            }
        }
        return hasUpper
                && hasLower
                && hasDigit
                && hasSpecial;
    }
}