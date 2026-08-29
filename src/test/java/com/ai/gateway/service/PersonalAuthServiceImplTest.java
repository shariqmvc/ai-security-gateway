package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.PersonalAuthServiceImpl;
import com.ai.gateway.personal.dto.PersonalLoginRequest;
import com.ai.gateway.personal.dto.PersonalSignupRequest;
import com.ai.gateway.personal.entity.PersonalAccount;
import com.ai.gateway.personal.entity.PersonalSession;
import com.ai.gateway.personal.entity.PersonalUser;
import com.ai.gateway.personal.repository.PersonalAccountRepository;
import com.ai.gateway.personal.repository.PersonalEmailVerificationTokenRepository;
import com.ai.gateway.personal.repository.PersonalSessionRepository;
import com.ai.gateway.personal.repository.PersonalUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalAuthServiceImplTest {

    @Mock
    PersonalUserRepository userRepository;

    @Mock
    PersonalAccountRepository accountRepository;

    @Mock
    PersonalSessionRepository sessionRepository;

    @Mock
    PersonalEmailVerificationTokenRepository verificationTokenRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    PersonalAuthServiceImpl service;

    @Test
    void signupCreatesIndependentPersonalUserAndAccount() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(userRepository.existsByEmailIgnoreCase("user@example.com"))
                .thenReturn(false);
        when(passwordEncoder.encode("very-secure-password"))
                .thenReturn("bcrypt-hash");

        when(userRepository.save(any(PersonalUser.class)))
                .thenAnswer(invocation -> {
                    PersonalUser user = invocation.getArgument(0);
                    user.setId(userId);
                    return user;
                });

        when(accountRepository.save(any(PersonalAccount.class)))
                .thenAnswer(invocation -> {
                    PersonalAccount account = invocation.getArgument(0);
                    account.setId(accountId);
                    return account;
                });

        var response = service.signup(
                new PersonalSignupRequest(
                        " USER@EXAMPLE.COM ",
                        "very-secure-password",
                        " User "));

        assertEquals(userId, response.userId());
        assertEquals(accountId, response.accountId());
        assertEquals("user@example.com", response.email());
        assertEquals("User", response.displayName());
        assertEquals("PERSONAL_FREE", response.plan());
        assertFalse(response.emailVerified());
        assertTrue(response.emailVerificationRequired());
        assertNull(response.verificationToken());

        verify(userRepository).save(any(PersonalUser.class));
        verify(accountRepository).save(any(PersonalAccount.class));
    }

    @Test
    void loginRejectsUnverifiedUser() {
        UUID userId = UUID.randomUUID();

        PersonalUser user = PersonalUser.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash("hash")
                .status("ACTIVE")
                .emailVerified(false)
                .build();

        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash"))
                .thenReturn(true);

        assertThrows(
                RuntimeException.class,
                () -> service.login(
                        new PersonalLoginRequest(
                                "user@example.com",
                                "password")));

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void bearerSessionProducesPersonalAuthenticationContext() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        PersonalUser user = PersonalUser.builder()
                .id(userId)
                .email("user@example.com")
                .passwordHash("hash")
                .status("ACTIVE")
                .emailVerified(true)
                .build();

        PersonalAccount account = PersonalAccount.builder()
                .id(accountId)
                .user(user)
                .plan("PERSONAL_FREE")
                .status("ACTIVE")
                .build();

        PersonalSession session = PersonalSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("hash")
                .createdAt(java.time.LocalDateTime.now().minusMinutes(1))
                .expiresAt(java.time.LocalDateTime.now().plusHours(1))
                .build();

        when(sessionRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(session));
        when(accountRepository.findByUserId(userId))
                .thenReturn(Optional.of(account));

        AuthenticationContext context =
                service.authenticateBearer("arp_test-token");

        assertTrue(context.isPersonalPrincipal());
        assertEquals(userId, context.getPersonalUserId());
        assertEquals(accountId, context.getPersonalAccountId());
        assertNull(context.getTenantId());
        assertFalse(context.isPlatformPrincipal());
        assertEquals(
                com.ai.gateway.authentication.AuthenticationType.PERSONAL_SESSION,
                context.getAuthenticationType());
    }
}
