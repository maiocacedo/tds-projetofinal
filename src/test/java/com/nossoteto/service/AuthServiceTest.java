package com.nossoteto.service;

import com.nossoteto.dto.AuthResponse;
import com.nossoteto.dto.LoginRequest;
import com.nossoteto.dto.RegisterRequest;
import com.nossoteto.dto.RegisterResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.UnauthorizedException;
import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "NossoTetoTestSecretKeyForJwtGenerationNeedsToBeVeryLong256BitsAtLeast";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, 7_200_000);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void registerShouldStoreBcryptHashAndReturnPublicFieldsOnly() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = authService.registerUser(new RegisterRequest("Ana", "ana@test.com", "segredo"));

        assertNotNull(response.getId());
        assertEquals("Ana", response.getName());
        assertEquals("ana@test.com", response.getEmail());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(passwordEncoder.matches("segredo", captor.getValue().getPasswordHash()));
    }

    @Test
    void registerShouldRejectMissingPassword() {
        assertThrows(BusinessRuleException.class,
                () -> authService.registerUser(new RegisterRequest("Ana", "ana@test.com", null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginShouldReturnTokenWithExpiresInMatchingTheRealTokenLifetime() {
        User user = new User();
        user.setId("user-1");
        user.setPasswordHash(passwordEncoder.encode("segredo"));
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.loginUser(new LoginRequest("ana@test.com", "segredo"));

        assertEquals("user-1", jwtTokenProvider.getUserIdFromToken(response.getToken()));
        assertEquals(7200, response.getExpiresIn());
    }

    @Test
    void loginWithWrongPasswordShouldThrowUnauthorized() {
        User user = new User();
        user.setId("user-1");
        user.setPasswordHash(passwordEncoder.encode("segredo"));
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class,
                () -> authService.loginUser(new LoginRequest("ana@test.com", "errada")));
    }

    @Test
    void loginWithUnknownEmailShouldThrowUnauthorized() {
        when(userRepository.findByEmail("ninguem@test.com")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> authService.loginUser(new LoginRequest("ninguem@test.com", "segredo")));
    }
}
