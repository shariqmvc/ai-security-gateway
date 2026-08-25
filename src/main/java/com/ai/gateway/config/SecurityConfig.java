package com.ai.gateway.config;

import com.ai.gateway.authentication.AuthenticationFilter;
import com.ai.gateway.ratelimit.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthenticationFilter authenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/public/**").permitAll()
                        /*
                         * /api/chat is authenticated by the mandatory
                         * AuthenticationFilter using X-API-Key. Spring Security
                         * must not apply a second role/authorization decision
                         * to this endpoint, because tenant identity and role
                         * are already established by that filter.
                         *
                         * AuthenticationFilter does NOT skip /api/chat:
                         * missing/invalid API keys therefore still fail with
                         * HTTP 401 before the controller is reached.
                         */
                        .requestMatchers("/api/chat").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        authenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        rateLimitFilter,
                        AuthenticationFilter.class);

        return http.build();
    }
}
