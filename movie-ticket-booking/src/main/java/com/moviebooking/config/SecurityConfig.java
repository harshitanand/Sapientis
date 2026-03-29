package com.moviebooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration.
 *
 * Production hardening (not wired here but discussed in DESIGN.md):
 *  - JWT validation via spring-security-oauth2-resource-server
 *  - Rate limiting (Bucket4j + Redis) per customer/IP
 *  - CORS allow-list for partner portals
 *  - Content-Security-Policy, HSTS, X-Frame-Options headers
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public read endpoints
                .requestMatchers(HttpMethod.GET,  "/shows/**").permitAll()
                .requestMatchers(HttpMethod.GET,  "/bookings/shows/*/seats").permitAll()
                // Swagger / OpenAPI
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs",
                    "/api-docs/**",
                    "/webjars/**",
                    "/actuator/health",
                    "/actuator/prometheus"
                ).permitAll()
                // All booking mutations require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
