package com.example.fileshare.auth.application;

import com.example.fileshare.auth.api.LoginResponse;
import com.example.fileshare.auth.api.SignupRequest;
import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.config.security.JwtTokenProvider;
import com.example.fileshare.user.api.UserResponse;
import com.example.fileshare.user.domain.User;
import com.example.fileshare.user.domain.UserRole;
import com.example.fileshare.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public User signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATED_EMAIL);
        }

        Instant now = Instant.now();
        User user = new User(
                null,
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                UserRole.USER,
                now,
                now
        );
        return userRepository.save(user);
    }

    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.generate(user);
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtTokenProvider.expiresInSeconds(),
                UserResponse.from(user)
        );
    }
}
