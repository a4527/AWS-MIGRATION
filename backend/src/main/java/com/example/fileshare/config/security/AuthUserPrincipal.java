package com.example.fileshare.config.security;

import com.example.fileshare.common.actor.Actor;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthUserPrincipal(
        Long userId,
        String email,
        String role
) {

    public Actor toActor() {
        return new Actor(userId, "ADMIN".equals(role));
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
