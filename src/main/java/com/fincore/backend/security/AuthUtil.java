package com.fincore.backend.security;

import org.springframework.security.core.Authentication;

public final class AuthUtil {

    private AuthUtil() {
    }

    public static boolean isAdmin(Authentication authentication) {

        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
}
