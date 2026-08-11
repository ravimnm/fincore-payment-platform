package com.fincore.backend.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fincore.backend.security.filter.JwtFilter;
import com.fincore.backend.security.oauth2.OAuth2LoginSuccessHandler;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    public SecurityConfig(JwtFilter jwtFilter, OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) {
        this.jwtFilter = jwtFilter;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;

    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                // Authentication
                .requestMatchers(
                    "/auth/register",
                    "/auth/login"
                ).permitAll()
                                   
                // OAuth2 endpoints
                .requestMatchers(
                    "/oauth2/**",
                    "/login/oauth2/**"
                ).permitAll()

                // Super Admin
                .requestMatchers("/admin/**")
                .hasAnyAuthority(
                    "ROLE_SUPER_ADMIN",
                    "ROLE_ADMIN"
                )

                // Wallet operations
                .requestMatchers("/wallet/**")
                .authenticated()

                // Payment operations
                .requestMatchers("/payments/**")
                .authenticated()

                // Ledger
                .requestMatchers("/ledger/**")
                .authenticated()

                // Everything else
                .anyRequest()
                .authenticated()
            )
            
            .oauth2Login(oauth2 ->
                oauth2
                    .successHandler(oAuth2LoginSuccessHandler)
            )

            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
