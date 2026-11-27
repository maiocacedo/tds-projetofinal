package com.nossoteto.service;

import com.nossoteto.dto.RegisterRequest;
import com.nossoteto.dto.RegisterResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.model.Permission;
import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, permissionRepository, passwordEncoder);
    }

    @Test
    void registerShouldStoreBcryptHashAndReturnPublicFieldsOnly() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = authService.registerUser(new RegisterRequest("Ana", "ana@test.com", "segredo1"));

        assertNotNull(response.getId());
        assertEquals("Ana", response.getName());
        assertEquals("ana@test.com", response.getEmail());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(passwordEncoder.matches("segredo1", captor.getValue().getPasswordHash()));
    }

    @Test
    void registerShouldGrantDefaultPermissions() {
        Permission manageProfile = new Permission(1, Permissions.MANAGE_PROFILE, "x");
        when(userRepository.findByEmail("novo@nossoteto.com")).thenReturn(Optional.empty());
        when(permissionRepository.findByNameNot(Permissions.ADMIN_USERS)).thenReturn(List.of(manageProfile));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.registerUser(new RegisterRequest("Novo", "novo@nossoteto.com", "senha123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Set.of(manageProfile), saved.getValue().getPermissions());
    }

    @Test
    void registerShouldRejectMissingOrBlankName() {
        BusinessRuleException nullEx = assertThrows(BusinessRuleException.class,
                () -> authService.registerUser(new RegisterRequest(null, "ana@test.com", "senha123")));
        assertEquals("name is required", nullEx.getMessage());

        BusinessRuleException blankEx = assertThrows(BusinessRuleException.class,
                () -> authService.registerUser(new RegisterRequest("   ", "ana@test.com", "senha123")));
        assertEquals("name is required", blankEx.getMessage());

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerShouldRejectInvalidEmail() {
        String tooLongEmail = "a".repeat(246) + "@test.com";
        List<String> invalidEmails = java.util.Arrays.asList(
                null,
                "",
                "   ",
                "abc",
                "a@b",
                "a b@c.com",
                "a@...",
                "a@.b.c",
                "a@b..c",
                "a@b.c.",
                tooLongEmail
        );
        for (String email : invalidEmails) {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> authService.registerUser(new RegisterRequest("Ana", email, "senha123")),
                    "Expected invalid email for: " + email);
            assertEquals("invalid email", ex.getMessage());
        }

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerShouldRejectRedosPayloadQuickly() {
        String redosEmail = "a@" + ".".repeat(20000) + "@";

        BusinessRuleException ex = assertTimeoutPreemptively(Duration.ofMillis(200), () ->
                assertThrows(BusinessRuleException.class,
                        () -> authService.registerUser(new RegisterRequest("Ana", redosEmail, "senha123"))));
        assertEquals("invalid email", ex.getMessage());

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerShouldRejectNullOrShortPassword() {
        BusinessRuleException nullEx = assertThrows(BusinessRuleException.class,
                () -> authService.registerUser(new RegisterRequest("Ana", "ana@test.com", null)));
        assertEquals("password must have at least 8 characters", nullEx.getMessage());

        BusinessRuleException shortEx = assertThrows(BusinessRuleException.class,
                () -> authService.registerUser(new RegisterRequest("Ana", "ana@test.com", "1234567")));
        assertEquals("password must have at least 8 characters", shortEx.getMessage());

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerShouldTrimNameAndEmailWhenValidWith8CharPassword() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterResponse response = authService.registerUser(
                new RegisterRequest("  Ana Silva  ", "  ana@test.com  ", "12345678"));

        assertEquals("Ana Silva", response.getName());
        assertEquals("ana@test.com", response.getEmail());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).findByEmail("ana@test.com");
        verify(userRepository).save(captor.capture());
        assertEquals("Ana Silva", captor.getValue().getName());
        assertEquals("ana@test.com", captor.getValue().getEmail());
        assertTrue(passwordEncoder.matches("12345678", captor.getValue().getPasswordHash()));
    }

    @Test
    void registerShouldRejectDuplicateEmail() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(new User()));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> authService.registerUser(new RegisterRequest("Ana", " ana@test.com ", "12345678")));
        assertEquals("Email already exists", ex.getMessage());

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerShouldValidateNameThenEmailThenPasswordBeforeDuplicateCheck() {
        BusinessRuleException first = assertThrows(BusinessRuleException.class,
                () -> authService.registerUser(new RegisterRequest(" ", "bad", "123")));
        assertEquals("name is required", first.getMessage());

        BusinessRuleException second = assertThrows(BusinessRuleException.class,
                () -> authService.registerUser(new RegisterRequest("Ana", "bad", "123")));
        assertEquals("invalid email", second.getMessage());

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }
}
