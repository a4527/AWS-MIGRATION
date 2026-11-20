package com.example.fileshare.auth.application;

import com.example.fileshare.common.error.ErrorCode;
import com.example.fileshare.common.exception.BusinessException;
import com.example.fileshare.config.security.JwtTokenProvider;
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

    public User signup(String email, String password, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.AUTH_DUPLICATED_EMAIL);
        }

        Instant now = Instant.now();
        User user = new User(
                null,
                email,
                passwordEncoder.encode(password),
                name,
                UserRole.USER,
                now,
                now
        );
        return userRepository.save(user);
    }

    public LoginResult login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.generate(user);
        return new LoginResult(user, accessToken, "Bearer", jwtTokenProvider.expiresInSeconds());
    }
}
