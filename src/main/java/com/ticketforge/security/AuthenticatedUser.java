package com.ticketforge.security;

public record AuthenticatedUser(String userId, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
