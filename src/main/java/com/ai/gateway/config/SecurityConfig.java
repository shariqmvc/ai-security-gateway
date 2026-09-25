package com.ai.gateway.config;

import com.ai.gateway.authentication.AuthenticationFilter;
import com.ai.gateway.ratelimit.filter.RateLimitFilter;
import com.ai.gateway.personal.ratelimit.filter.PersonalRateLimitFilter;
import com.ai.gateway.personal.apikey.filter.PersonalApiKeyScopeFilter;
import jakarta.servlet.DispatcherType;
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
    private final PersonalRateLimitFilter personalRateLimitFilter;
    private final PersonalApiKeyScopeFilter personalApiKeyScopeFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                       // .requestMatchers("/actuator/health", "/public/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/api/health",
                                "/actuator/health",
                                "/public/**",
                                "/api/personal/billing/webhooks"
                        )
                        .permitAll()
                        /*
                         * StreamingResponseBody uses an ASYNC servlet dispatch
                         * after the authenticated REQUEST dispatch has returned.
                         * AuthenticationFilter intentionally does not run again
                         * for that dispatch, so the SecurityContext is empty by
                         * the time Spring Security evaluates authorization.
                         *
                         * The original request was already authenticated by
                         * AuthenticationFilter before /api/chat was permitted.
                         * Allowing only the internal ASYNC continuation prevents
                         * Spring Security from rejecting the completed SSE stream
                         * as anonymous after the response has been committed.
                         */
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
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
                        AuthenticationFilter.class)
                .addFilterAfter(
                        personalRateLimitFilter,
                        RateLimitFilter.class)
                .addFilterAfter(
                        personalApiKeyScopeFilter,
                        PersonalRateLimitFilter.class);

        return http.build();
    }
}
