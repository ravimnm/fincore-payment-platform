package com.fincore.backend.security.oauth2;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.fincore.backend.entity.Role;
import com.fincore.backend.entity.User;
import com.fincore.backend.enums.RoleName;
import com.fincore.backend.enums.UserStatus;
import com.fincore.backend.repository.RoleRepository;
import com.fincore.backend.repository.UserRepository;
import com.fincore.backend.security.jwt.JwtUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginSuccessHandler
        implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;

    public OAuth2LoginSuccessHandler(
            UserRepository userRepository,
            RoleRepository roleRepository,
            JwtUtil jwtUtil) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oauthUser =
                (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        if (email == null || email.isBlank()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Email not provided by OAuth provider"
            );
            return;
        }

        User user = userRepository
                .findByEmail(email)
                .orElseGet(() ->
                        createOAuthUser(email, name)
                );

        if (user.getStatus() != UserStatus.ACTIVE) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "User account is not active"
            );
            return;
        }

        Set<String> roles = user.getRoles()
                .stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());

        String token =
                jwtUtil.generateToken(
                        user.getUsername(),
                        roles
                );

        /*
         * Development response.
         *
         * For a production SPA/mobile application,
         * use a secure one-time authorization code instead
         * of putting the JWT in the URL.
         */
        response.setContentType("application/json");
        response.getWriter().write(
                """
                {
                    "token": "%s",
                    "username": "%s",
                    "roles": %s
                }
                """.formatted(
                        token,
                        user.getUsername(),
                        roles
                )
        );
    }

    private User createOAuthUser(
            String email,
            String name) {

        String username =
                generateUniqueUsername(email);

        Role userRole =
                roleRepository
                        .findByName(RoleName.ROLE_USER)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "ROLE_USER not found"
                                )
                        );

        User user = new User();

        user.setUsername(username);
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(userRole);

        if (name != null && !name.isBlank()) {

            String[] parts = name.trim().split("\\s+", 2);

            user.setFirstName(parts[0]);

            if (parts.length > 1) {
                user.setLastName(parts[1]);
            }
        }

        return userRepository.save(user);
    }

    private String generateUniqueUsername(String email) {

        String base =
                email.substring(
                        0,
                        email.indexOf("@")
                )
                .replaceAll("[^a-zA-Z0-9_]", "_");

        String username = base;
        int counter = 1;

        while (userRepository.existsByUsername(username)) {

            username = base + counter;
            counter++;
        }

        return username;
    }
}
