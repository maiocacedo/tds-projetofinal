package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.PointLedgerEntry;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.PointLedgerRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import com.nossoteto.support.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GroceryEnhancementsIntegrationTest {

    private static final Instant BASE_TIME = Instant.parse("2026-11-27T15:00:00Z");

    static class MutableClock extends Clock {
        private volatile Instant pinned;

        void pin(Instant instant) { this.pinned = instant; }
        void release() { this.pinned = null; }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return pinned != null ? pinned : Instant.now(); }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock groceryTestClock() {
            return new MutableClock();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private GroceryRepository groceryRepository;
    @Autowired private PointLedgerRepository pointLedgerRepository;
    @Autowired private MutableClock clock;

    @BeforeEach
    void pinClock() {
        clock.pin(BASE_TIME);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    private record Fixture(String houseId, String ana, String bruno, String carla) {
        String groceries() { return "/api/houses/" + houseId + "/groceries"; }
        String item(String itemId) { return groceries() + "/" + itemId; }
    }

    private String registerUser(String name) throws Exception {
        String email = "groceries-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"senhaSegura123\"}"))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private Fixture houseWithThreeResidents() throws Exception {
        String ana = registerUser("Ana");
        String bruno = registerUser("Bruno");
        String carla = registerUser("Carla");

        MvcResult house = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa Mercado " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String houseId = json(house).get("id").asText();

        for (String member : List.of(bruno, carla)) {
            User user = userRepository.findById(member).orElseThrow();
            user.setHouseId(houseId);
            userRepository.save(user);
        }
        return new Fixture(houseId, ana, bruno, carla);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createItem(Fixture f, String userId, String itemName, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post(f.groceries())
                        .with(TestTokens.jwtForUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"" + itemName + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private List<PointLedgerEntry> groceryCredits(String userId, String itemId) {
        return pointLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(e -> itemId.equals(e.getSourceId()))
                .toList();
    }

    @Test
    void endpointsRequireAuthenticationRoleScopeAndHouseMembership() throws Exception {
        String base = "/api/houses/h1/groceries";
        String someone = UUID.randomUUID().toString();
        List<Supplier<MockHttpServletRequestBuilder>> writes = List.of(
                () -> put(base + "/i1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Arroz\",\"quantity\":1}"),
                () -> delete(base + "/i1"),
                () -> put(base + "/i1/assign"),
                () -> delete(base + "/i1/assign"),
                () -> put(base + "/i1/purchase"),
                () -> put(base + "/i1/unpurchase"));

        for (Supplier<MockHttpServletRequestBuilder> request : writes) {
            mockMvc.perform(request.get()).andExpect(status().isUnauthorized());
            mockMvc.perform(request.get().with(TestTokens.jwtWith(someone, "SCOPE_read", "SCOPE_write")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(request.get().with(TestTokens.jwtWith(someone, Permissions.MANAGE_GROCERIES, "SCOPE_read")))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get(base)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base).with(TestTokens.jwtWith(someone, "SCOPE_read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(base).with(TestTokens.jwtWith(someone, Permissions.MANAGE_GROCERIES, "SCOPE_write")))
                .andExpect(status().isForbidden());

        Fixture f = houseWithThreeResidents();
        String itemId = createItem(f, f.ana(), "Café", 1);
        String outsider = registerUser("Fora");

        mockMvc.perform(get(f.groceries()).with(TestTokens.jwtForUser(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(f.item(itemId)).with(TestTokens.jwtForUser(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Chá\",\"quantity\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(f.item(itemId)).with(TestTokens.jwtForUser(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(f.item(itemId) + "/unpurchase").with(TestTokens.jwtForUser(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listFiltersPurchasedItemsToLastSevenDaysAndEnrichesUserRefs() throws Exception {
        Fixture f = houseWithThreeResidents();

        String openId = createItem(f, f.ana(), "Pão", 2);

        clock.pin(BASE_TIME.minus(6, ChronoUnit.DAYS));
        String recentId = createItem(f, f.ana(), "Queijo", 1);
        mockMvc.perform(put(f.item(recentId) + "/purchase").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk());

        clock.pin(BASE_TIME.minus(8, ChronoUnit.DAYS));
        String oldId = createItem(f, f.ana(), "Manteiga", 1);
        mockMvc.perform(put(f.item(oldId) + "/purchase").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk());

        Grocery legacyPurchased = new Grocery();
        legacyPurchased.setId(UUID.randomUUID().toString());
        legacyPurchased.setHouseId(f.houseId());
        legacyPurchased.setItemName("Legado");
        legacyPurchased.setQuantity(1);
        legacyPurchased.setIsPurchased(true);
        legacyPurchased.setPurchasedAt(null);
        groceryRepository.save(legacyPurchased);

        clock.pin(BASE_TIME);
        MvcResult listed = mockMvc.perform(get(f.groceries()).with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        JsonNode items = json(listed);
        List<String> ids = List.of(items.get(0).get("id").asText(), items.get(1).get("id").asText());
        assertTrue(ids.contains(openId));
        assertTrue(ids.contains(recentId));
        assertFalse(ids.contains(oldId));
        assertFalse(ids.contains(legacyPurchased.getId()));

        for (JsonNode node : items) {
            if (recentId.equals(node.get("id").asText())) {
                assertEquals(f.ana(), node.get("createdBy").get("id").asText());
                assertEquals("Ana", node.get("createdBy").get("name").asText());
                assertEquals(f.bruno(), node.get("assignee").get("id").asText());
                assertEquals("Bruno", node.get("assignee").get("name").asText());
                assertEquals(f.bruno(), node.get("purchasedBy").get("id").asText());
                assertEquals("Bruno", node.get("purchasedBy").get("name").asText());
                assertFalse(node.get("purchasedAt").isNull());
            }
        }
    }

    @Test
    void editAndDeleteAllowedForAnyResidentWhileUnpurchasedAndBlockedWhenPurchased() throws Exception {
        Fixture f = houseWithThreeResidents();
        String itemId = createItem(f, f.ana(), "Sabonete", 2);

        mockMvc.perform(put(f.item(itemId))
                        .with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Sabonete líquido\",\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value("Sabonete líquido"))
                .andExpect(jsonPath("$.quantity").value(4));

        String deleteTarget = createItem(f, f.ana(), "Esponja", 1);
        mockMvc.perform(delete(f.item(deleteTarget)).with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isNoContent());
        assertFalse(groceryRepository.findById(deleteTarget).isPresent());

        mockMvc.perform(put(f.item(itemId) + "/purchase").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk());

        mockMvc.perform(put(f.item(itemId))
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemName\":\"Outro\",\"quantity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("item already purchased"));

        mockMvc.perform(delete(f.item(itemId)).with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("item already purchased"));
    }

    @Test
    void assignOnlyFreeAndUnassignOnlyAssignee() throws Exception {
        Fixture f = houseWithThreeResidents();
        String itemId = createItem(f, f.ana(), "Açúcar", 1);

        mockMvc.perform(delete(f.item(itemId) + "/assign").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the assignee can unassign"));

        mockMvc.perform(put(f.item(itemId) + "/assign").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(f.bruno()))
                .andExpect(jsonPath("$.assignee.name").value("Bruno"));

        mockMvc.perform(put(f.item(itemId) + "/assign").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("item already assigned"));

        mockMvc.perform(put(f.item(itemId) + "/assign").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("item already assigned"));

        mockMvc.perform(delete(f.item(itemId) + "/assign").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the assignee can unassign"));

        mockMvc.perform(delete(f.item(itemId) + "/assign").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.assignee").doesNotExist());
    }

    @Test
    void buyAnywayCreditsFivePointsWhenAnotherAddedAndUnpurchaseWithinTenMinutesRevertsBoth() throws Exception {
        Fixture f = houseWithThreeResidents();
        String itemId = createItem(f, f.ana(), "Macarrão", 2);

        mockMvc.perform(put(f.item(itemId) + "/assign").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk());

        // Carla buys the item Bruno had claimed ("Comprei mesmo assim")
        mockMvc.perform(put(f.item(itemId) + "/purchase").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPurchased").value(true))
                .andExpect(jsonPath("$.assigneeId").value(f.carla()))
                .andExpect(jsonPath("$.assignee.id").value(f.carla()))
                .andExpect(jsonPath("$.purchasedBy.id").value(f.carla()))
                .andExpect(jsonPath("$.purchasedAt").exists());

        List<PointLedgerEntry> credits = groceryCredits(f.carla(), itemId);
        assertEquals(1, credits.size());
        assertEquals(5, credits.get(0).getAmount());
        assertEquals(PointReason.GROCERY_BOUGHT, credits.get(0).getReason());

        // Repurchasing an already purchased item returns 409
        mockMvc.perform(put(f.item(itemId) + "/purchase").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("item already purchased"));

        // Self-created item yields 0 points when purchased by creator
        String selfItemId = createItem(f, f.ana(), "Sal", 1);
        mockMvc.perform(put(f.item(selfItemId) + "/purchase").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isOk());
        assertTrue(groceryCredits(f.ana(), selfItemId).isEmpty());

        // Another resident cannot unpurchase
        mockMvc.perform(put(f.item(itemId) + "/unpurchase").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the buyer can unpurchase"));

        // Carla unpurchases at +9 minutes -> restores Bruno and removes +5
        clock.pin(BASE_TIME.plus(9, ChronoUnit.MINUTES));
        mockMvc.perform(put(f.item(itemId) + "/unpurchase").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPurchased").value(false))
                .andExpect(jsonPath("$.assigneeId").value(f.bruno()))
                .andExpect(jsonPath("$.assignee.name").value("Bruno"))
                .andExpect(jsonPath("$.purchasedBy").doesNotExist())
                .andExpect(jsonPath("$.purchasedAt").doesNotExist());

        assertTrue(groceryCredits(f.carla(), itemId).isEmpty());

        // Re-purchasing after unpurchase credits +5 again
        mockMvc.perform(put(f.item(itemId) + "/purchase").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk());
        assertEquals(1, groceryCredits(f.carla(), itemId).size());

        // After 10 minutes -> 400 "unpurchase window expired"
        clock.pin(BASE_TIME.plus(9, ChronoUnit.MINUTES).plus(10, ChronoUnit.MINUTES).plusSeconds(1));
        mockMvc.perform(put(f.item(itemId) + "/unpurchase").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unpurchase window expired"));
    }

    @Test
    void unpurchaseSetsAssigneeNullWhenPreviousAssigneeLeftHouse() throws Exception {
        Fixture f = houseWithThreeResidents();
        String itemId = createItem(f, f.ana(), "Óleo", 1);

        mockMvc.perform(put(f.item(itemId) + "/assign").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk());
        mockMvc.perform(put(f.item(itemId) + "/purchase").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/houses/" + f.houseId() + "/leave").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isNoContent());

        mockMvc.perform(put(f.item(itemId) + "/unpurchase").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPurchased").value(false))
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.assignee").doesNotExist());

        Grocery saved = groceryRepository.findById(itemId).orElseThrow();
        assertFalse(saved.getIsPurchased());
    }
}
