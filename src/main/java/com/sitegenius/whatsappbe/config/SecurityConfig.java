package com.sitegenius.whatsappbe.config;

import com.sitegenius.whatsappbe.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        // When authenticated user does not have required role
        AccessDeniedHandler forbiddenHandler =
                (request, response, accessDeniedException) -> {

                    response.sendError(
                            403,
                            "Access Denied"
                    );
                };

        http
                // Disable CSRF because we are using JWT
                .csrf(csrf -> csrf.disable())

                // Enable CORS
                .cors(cors -> {})

                // JWT based authentication = stateless
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Handle authorization errors
                .exceptionHandling(exception ->
                        exception
                                .accessDeniedHandler(forbiddenHandler)
                )

                .authorizeHttpRequests(auth -> auth

                        // Swagger
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()


                        // Public authentication endpoints
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/signup"
                        ).permitAll()

                        .requestMatchers(
                                "/api/webhook/whatsapp"
                        ).permitAll()


                        // SUPER_ADMIN can create Organization Admin
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users/organization/*/admin"
                        ).hasRole("SUPER_ADMIN")

                        // Only ADMIN can create users/agents
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users"
                        ).hasRole("ADMIN")

                        // Remaining API endpoints require login
                        .requestMatchers(
                                "/api/**"
                        ).authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // JWT filter
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}