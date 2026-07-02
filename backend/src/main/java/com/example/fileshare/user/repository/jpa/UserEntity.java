package com.example.fileshare.user.repository.jpa;

import com.example.fileshare.user.domain.User;
import com.example.fileshare.user.domain.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    private UserEntity(String email, String passwordHash, String name, UserRole role, Instant createdAt, Instant updatedAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserEntity from(User user) {
        UserEntity entity = new UserEntity(
                user.email(),
                user.passwordHash(),
                user.name(),
                user.role(),
                user.createdAt(),
                user.updatedAt()
        );
        entity.id = user.id();
        return entity;
    }

    public User toDomain() {
        return new User(id, email, passwordHash, name, role, createdAt, updatedAt);
    }
}
