package com.nossoteto.integration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationMatrixIntegrationTest {

    private static final String DENIED = "Insufficient permission";

    @Autowired private MockMvc mockMvc;

    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of("GET", "/api/users/me", "ROLE_MANAGE_PROFILE", "read"),
                Arguments.of("PUT", "/api/users/me/profile", "ROLE_MANAGE_PROFILE", "write"),
                Arguments.of("GET", "/api/houses", "ROLE_SEARCH_HOUSES", "read"),
                Arguments.of("GET", "/api/houses/h1", "ROLE_SEARCH_HOUSES", "read"),
                Arguments.of("POST", "/api/houses", "ROLE_MANAGE_HOUSE", "write"),
                Arguments.of("PUT", "/api/houses/h1", "ROLE_MANAGE_HOUSE", "write"),
                Arguments.of("DELETE", "/api/houses/h1/members/u2", "ROLE_MANAGE_HOUSE", "write"),
                Arguments.of("POST", "/api/matches/requests", "ROLE_MATCH", "write"),
                Arguments.of("GET", "/api/houses/h1/matches", "ROLE_MATCH", "read"),
                Arguments.of("POST", "/api/matches/requests/r1/vote", "ROLE_MATCH", "write"),
                Arguments.of("POST", "/api/matches/requests/cancel-others", "ROLE_MATCH", "write"),
                Arguments.of("GET", "/api/houses/h1/expenses", "ROLE_MANAGE_EXPENSES", "read"),
                Arguments.of("GET", "/api/houses/h1/expenses/e1/attachment", "ROLE_MANAGE_EXPENSES", "read"),
                Arguments.of("POST", "/api/houses/h1/expenses", "ROLE_MANAGE_EXPENSES", "write"),
                Arguments.of("POST", "/api/houses/h1/expenses/e1/propose-split", "ROLE_MANAGE_EXPENSES", "write"),
                Arguments.of("POST", "/api/houses/h1/expenses/e1/vote-split", "ROLE_MANAGE_EXPENSES", "write"),
                Arguments.of("PUT", "/api/houses/h1/expenses/e1/pay", "ROLE_MANAGE_EXPENSES", "write"),
                Arguments.of("GET", "/api/houses/h1/chores", "ROLE_MANAGE_CHORES", "read"),
                Arguments.of("POST", "/api/houses/h1/chores", "ROLE_MANAGE_CHORES", "write"),
                Arguments.of("PUT", "/api/houses/h1/chores/c1/complete", "ROLE_MANAGE_CHORES", "write"),
                Arguments.of("POST", "/api/houses/h1/chores/rotate", "ROLE_MANAGE_CHORES", "write"),
                Arguments.of("GET", "/api/houses/h1/groceries", "ROLE_MANAGE_GROCERIES", "read"),
                Arguments.of("POST", "/api/houses/h1/groceries", "ROLE_MANAGE_GROCERIES", "write"),
                Arguments.of("PUT", "/api/houses/h1/groceries/g1/assign", "ROLE_MANAGE_GROCERIES", "write"),
                Arguments.of("PUT", "/api/houses/h1/groceries/g1/purchase", "ROLE_MANAGE_GROCERIES", "write"),
                Arguments.of("GET", "/api/houses/h1/dashboard", "ROLE_VIEW_DASHBOARD", "read"),
                Arguments.of("GET", "/api/admin/users", "ROLE_ADMIN_USERS", "read"),
                Arguments.of("PUT", "/api/admin/users/u1/permissions", "ROLE_ADMIN_USERS", "write"));
    }

    private MockHttpServletRequestBuilder req(String method, String url) {
        return request(HttpMethod.valueOf(method), url).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @ParameterizedTest(name = "{0} {1} sem permissao -> 403")
    @MethodSource("matrix")
    void withoutPermissionIsForbidden(String method, String url, String permission, String scope) throws Exception {
        mockMvc.perform(req(method, url).with(jwt().jwt(j -> j.subject("matrix-user"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_read"), new SimpleGrantedAuthority("SCOPE_write"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(DENIED));
    }

    @ParameterizedTest(name = "{0} {1} scope errado -> 403")
    @MethodSource("matrix")
    void withWrongScopeIsForbidden(String method, String url, String permission, String scope) throws Exception {
        String wrong = "read".equals(scope) ? "SCOPE_write" : "SCOPE_read";
        mockMvc.perform(req(method, url).with(jwt().jwt(j -> j.subject("matrix-user"))
                        .authorities(new SimpleGrantedAuthority(permission), new SimpleGrantedAuthority(wrong))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(DENIED));
    }

    @ParameterizedTest(name = "{0} {1} permissao+scope -> passa da camada de permissao")
    @MethodSource("matrix")
    void withPermissionAndScopePassesTheAuthorityLayer(String method, String url, String permission, String scope) throws Exception {
        MvcResult r = mockMvc.perform(req(method, url).with(jwt().jwt(j -> j.subject("matrix-user"))
                        .authorities(new SimpleGrantedAuthority(permission), new SimpleGrantedAuthority("SCOPE_" + scope))))
                .andReturn();
        // Downstream may answer 400/403/404 (house checks, unknown user "matrix-user"), but never the authority-layer denial.
        assertFalse(r.getResponse().getContentAsString().contains(DENIED), method + " " + url);
    }

    @org.junit.jupiter.api.Test
    void leaveHouseNeedsOnlyWriteScope() throws Exception {
        MvcResult r = mockMvc.perform(req("POST", "/api/houses/h1/leave")
                        .with(jwt().jwt(j -> j.subject("matrix-user")).authorities(new SimpleGrantedAuthority("SCOPE_write"))))
                .andReturn();
        assertFalse(r.getResponse().getContentAsString().contains(DENIED));
        mockMvc.perform(req("POST", "/api/houses/h1/leave")
                        .with(jwt().jwt(j -> j.subject("matrix-user")).authorities(new SimpleGrantedAuthority("SCOPE_read"))))
                .andExpect(status().isForbidden());
    }
}
