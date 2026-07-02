package com.example.fileshare.user.repository;

import com.example.fileshare.user.domain.User;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, Long> idsByEmail = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public User save(User user) {
        User saved = user;
        if (saved.id() == null) {
            saved = new User(
                    sequence.getAndIncrement(),
                    user.email(),
                    user.passwordHash(),
                    user.name(),
                    user.role(),
                    user.createdAt(),
                    user.updatedAt()
            );
        }
        usersById.put(saved.id(), saved);
        idsByEmail.put(saved.email(), saved.id());
        return saved;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        Long id = idsByEmail.get(email);
        if (id == null) {
            return Optional.empty();
        }
        return findById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return idsByEmail.containsKey(email);
    }
}
