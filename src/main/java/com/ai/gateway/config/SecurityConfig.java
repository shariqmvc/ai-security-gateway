package com.ai.gateway.config;

import com.ai.gateway.authentication.AuthenticationFilter;
import com.ai.gateway.ratelimit.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationFilter authenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/tenants/**").permitAll()
                        .requestMatchers("/admin/entitlements/**").permitAll()
                        .anyRequest().authenticated()
                )
            /*    .authorizeHttpRequests(auth -> auth
                        .anyRequest()
                        .permitAll()) */

                .addFilterBefore(
                        authenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        rateLimitFilter,
                        AuthenticationFilter.class);

        return http.build();
    }

}
