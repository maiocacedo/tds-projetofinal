package com.nossoteto.service;

import com.nossoteto.dto.AdminUserResponse;
import com.nossoteto.dto.PermissionDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.House;
import com.nossoteto.model.Permission;
import com.nossoteto.model.User;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.RefreshTokenRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-26T20:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final HouseRepository houses = mock(HouseRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AdminService service = new AdminService(users, permissions, houses, refreshTokens, clock);

    private User user(String id) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        u.setEmail(id + "@x.com");
        return u;
    }

    private House house(String id, String name) {
        House h = new House();
        h.setId(id);
        h.setName(name);
        return h;
    }

    @Test
    void listsUsersWithBatchLoadedHouseNames() {
        User u1 = user("u1");
        u1.setHouseId("h1");
        User u2 = user("u2");
        User u3 = user("u3");
        u3.setHouseId("h1");
        User u4 = user("u4");
        u4.setHouseId("h-missing");

        when(users.findAll()).thenReturn(List.of(u1, u2, u3, u4));
        when(houses.findAllById(Set.of("h1", "h-missing"))).thenReturn(List.of(house("h1", "Casa Verde")));
        when(users.findPermissionNamesByUserId("u1")).thenReturn(List.of("ROLE_MATCH"));
        when(users.findPermissionNamesByUserId("u2")).thenReturn(List.of("ROLE_MANAGE_PROFILE"));
        when(users.findPermissionNamesByUserId("u3")).thenReturn(List.of());
        when(users.findPermissionNamesByUserId("u4")).thenReturn(List.of());

        List<AdminUserResponse> result = service.listUsers();

        assertEquals(4, result.size());
        assertEquals("Casa Verde", result.get(0).houseName());
        assertNull(result.get(1).houseId());
        assertNull(result.get(1).houseName());
        assertEquals("Casa Verde", result.get(2).houseName());
        assertNull(result.get(3).houseName());
        verify(houses, times(1)).findAllById(Set.of("h1", "h-missing"));
        verifyNoMoreInteractions(houses);
    }

    @Test
    void listsPermissionsOrderedByIdWithFallbackDescription() {
        Permission p1 = new Permission(1, "ROLE_MANAGE_PROFILE", "Ver e editar o proprio perfil");
        Permission p2 = new Permission(2, "ROLE_BLANK_DESC", "   ");
        Permission p3 = new Permission(3, "ROLE_NULL_DESC", null);
        when(permissions.findAll(Sort.by("id"))).thenReturn(List.of(p1, p2, p3));

        List<PermissionDto> result = service.listPermissions();

        assertEquals(List.of(
                new PermissionDto("ROLE_MANAGE_PROFILE", "Ver e editar o proprio perfil"),
                new PermissionDto("ROLE_BLANK_DESC", "ROLE_BLANK_DESC"),
                new PermissionDto("ROLE_NULL_DESC", "ROLE_NULL_DESC")
        ), result);
        verify(permissions).findAll(Sort.by("id"));
    }

    @Test
    void replacesTheWholePermissionSetRevokesRefreshTokensAndReturnsHouseName() {
        User target = user("t1");
        target.setHouseId("h1");
        Permission match = new Permission(4, "ROLE_MATCH", "Solicitar, listar e votar matches");
        when(users.findById("t1")).thenReturn(Optional.of(target));
        when(permissions.findByNameIn(Set.of("ROLE_MATCH"))).thenReturn(List.of(match));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findPermissionNamesByUserId("t1")).thenReturn(List.of("ROLE_MATCH"));
        when(houses.findById("h1")).thenReturn(Optional.of(house("h1", "Casa Azul")));

        var response = service.replacePermissions("admin", "t1", List.of("ROLE_MATCH"));

        assertEquals(Set.of(match), target.getPermissions());
        assertEquals(List.of("ROLE_MATCH"), response.permissions());
        assertEquals("h1", response.houseId());
        assertEquals("Casa Azul", response.houseName());
        verify(refreshTokens).revokeAllByUserId("t1", LocalDateTime.now(clock));
    }

    @Test
    void unknownPermissionIsRejectedWithoutRevokingTokens() {
        when(users.findById("t1")).thenReturn(Optional.of(user("t1")));
        when(permissions.findByNameIn(Set.of("ROLE_GOD"))).thenReturn(List.of());
        assertThrows(BusinessRuleException.class, () -> service.replacePermissions("admin", "t1", List.of("ROLE_GOD")));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void nullListIsRejectedWithoutRevokingTokens() {
        assertThrows(BusinessRuleException.class, () -> service.replacePermissions("admin", "t1", null));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void nullOrBlankPermissionEntryIsRejectedWithoutRevokingTokens() {
        when(users.findById("t1")).thenReturn(Optional.of(user("t1")));
        assertThrows(BusinessRuleException.class,
                () -> service.replacePermissions("admin", "t1", Arrays.asList("ROLE_MATCH", null)));
        assertThrows(BusinessRuleException.class,
                () -> service.replacePermissions("admin", "t1", List.of("  ")));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void adminCannotDropOwnAdminPermissionWithoutRevokingTokens() {
        when(users.findById("admin")).thenReturn(Optional.of(user("admin")));
        assertThrows(BusinessRuleException.class,
                () -> service.replacePermissions("admin", "admin", List.of("ROLE_MATCH")));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void unknownUserIs404WithoutRevokingTokens() {
        when(users.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.replacePermissions("admin", "ghost", List.of()));
        verifyNoInteractions(refreshTokens);
    }
}
