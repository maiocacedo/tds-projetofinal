package com.nossoteto.service;

import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Permission;
import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final AdminService service = new AdminService(users, permissions);

    private User user(String id) {
        User u = new User();
        u.setId(id);
        u.setEmail(id + "@x.com");
        return u;
    }

    @Test
    void replacesTheWholePermissionSet() {
        User target = user("t1");
        Permission match = new Permission(4, "ROLE_MATCH", "");
        when(users.findById("t1")).thenReturn(Optional.of(target));
        when(permissions.findByNameIn(Set.of("ROLE_MATCH"))).thenReturn(List.of(match));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findPermissionNamesByUserId("t1")).thenReturn(List.of("ROLE_MATCH"));

        var response = service.replacePermissions("admin", "t1", List.of("ROLE_MATCH"));

        assertEquals(Set.of(match), target.getPermissions());
        assertEquals(List.of("ROLE_MATCH"), response.permissions());
    }

    @Test
    void unknownPermissionIsRejected() {
        when(users.findById("t1")).thenReturn(Optional.of(user("t1")));
        when(permissions.findByNameIn(Set.of("ROLE_GOD"))).thenReturn(List.of());
        assertThrows(BusinessRuleException.class, () -> service.replacePermissions("admin", "t1", List.of("ROLE_GOD")));
    }

    @Test
    void nullListIsRejected() {
        assertThrows(BusinessRuleException.class, () -> service.replacePermissions("admin", "t1", null));
    }

    @Test
    void nullOrBlankPermissionEntryIsRejected() {
        when(users.findById("t1")).thenReturn(Optional.of(user("t1")));
        assertThrows(BusinessRuleException.class,
                () -> service.replacePermissions("admin", "t1", Arrays.asList("ROLE_MATCH", null)));
        assertThrows(BusinessRuleException.class,
                () -> service.replacePermissions("admin", "t1", List.of("  ")));
    }

    @Test
    void adminCannotDropOwnAdminPermission() {
        when(users.findById("admin")).thenReturn(Optional.of(user("admin")));
        assertThrows(BusinessRuleException.class,
                () -> service.replacePermissions("admin", "admin", List.of("ROLE_MATCH")));
    }

    @Test
    void unknownUserIs404() {
        when(users.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.replacePermissions("admin", "ghost", List.of()));
    }
}
