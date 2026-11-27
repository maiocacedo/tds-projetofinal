package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.Chore;
import com.nossoteto.model.PointLedgerEntry;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreSwapStatus;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.ChoreSwapRepository;
import com.nossoteto.repository.PointLedgerRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import com.nossoteto.service.ChoreService;
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
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChoreEnhancementsIntegrationTest {

    /** Friday 2026-11-27 12:00 and Sunday 2026-11-29 00:00 in São Paulo: both in ISO week 2026-W48. */
    private static final Instant FRIDAY = Instant.parse("2026-11-27T15:00:00Z");
    private static final Instant SUNDAY_SAME_WEEK = Instant.parse("2026-11-29T03:00:00Z");
    private static final Instant NEXT_SUNDAY = Instant.parse("2026-12-06T03:00:00Z");
    private static final Instant SUNDAY_AFTER = Instant.parse("2026-12-13T03:00:00Z");

    /** Clock that follows real time unless a test pins it to an instant. */
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
        MutableClock choreTestClock() {
            return new MutableClock();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ChoreRepository choreRepository;
    @Autowired private ChoreSwapRepository choreSwapRepository;
    @Autowired private PointLedgerRepository pointLedgerRepository;
    @Autowired private ChoreService choreService;
    @Autowired private MutableClock clock;

    @BeforeEach
    void pinFriday() {
        clock.pin(FRIDAY);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    /** Ana created the house (admin); Bruno and Carla are plain residents. */
    private record Fixture(String houseId, String ana, String bruno, String carla) {
        String chores() { return "/api/houses/" + houseId + "/chores"; }
        String chore(String choreId) { return chores() + "/" + choreId; }
    }

    private String registerUser(String name) throws Exception {
        String email = "chores-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"senhaSegura123\"}"))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private Fixture houseWithThreeResidents() throws Exception {
        String ana = registerUser("Ana Admin");
        String bruno = registerUser("Bruno Morador");
        String carla = registerUser("Carla Moradora");

        MvcResult house = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa Tarefas " + UUID.randomUUID() + "\"}"))
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

    private String order(String... ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(ids[i]).append('"');
        }
        return sb.append(']').toString();
    }

    /** Creates a rotational chore owned by {@code owner} with an explicit rotation order. */
    private String createChore(Fixture f, String description, String frequency, String owner, String rotation)
            throws Exception {
        String body = "{\"description\":\"" + description + "\",\"choreType\":\"ROTATIONAL\",\"frequency\":\""
                + frequency + "\",\"assigneeId\":\"" + owner + "\",\"rotationOrder\":" + rotation + "}";
        MvcResult result = mockMvc.perform(post(f.chores())
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private void complete(Fixture f, String choreId, String userId) throws Exception {
        mockMvc.perform(put(f.chore(choreId) + "/complete").with(TestTokens.jwtForUser(userId)))
                .andExpect(status().isOk());
    }

    private void close(Fixture f) throws Exception {
        mockMvc.perform(post(f.chores() + "/rotate").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isOk());
    }

    private List<PointLedgerEntry> choreCredits(String userId, String choreId) {
        return pointLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(e -> choreId.equals(e.getSourceId()))
                .toList();
    }

    private JsonNode choreJson(Fixture f, String choreId) throws Exception {
        MvcResult result = mockMvc.perform(get(f.chores()).with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : json(result)) {
            if (choreId.equals(node.get("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("chore not listed: " + choreId);
    }

    // ------------------------------------------------------------------ security

    @Test
    void newEndpointsRequireAuthenticationRoleAndScope() throws Exception {
        String base = "/api/houses/h1/chores";
        String someone = UUID.randomUUID().toString();
        List<Supplier<MockHttpServletRequestBuilder>> writes = List.of(
                () -> put(base + "/c1/uncomplete"),
                () -> put(base + "/c1/reopen"),
                () -> put(base + "/c1/rotation").contentType(MediaType.APPLICATION_JSON).content("{\"order\":[]}"),
                () -> post(base + "/c1/swaps").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetChoreId\":\"c2\"}"),
                () -> put(base + "/swaps/s1/accept"),
                () -> put(base + "/swaps/s1/reject"));

        for (Supplier<MockHttpServletRequestBuilder> request : writes) {
            mockMvc.perform(request.get()).andExpect(status().isUnauthorized());
            mockMvc.perform(request.get().with(TestTokens.jwtWith(someone, "SCOPE_read", "SCOPE_write")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(request.get().with(TestTokens.jwtWith(someone, Permissions.MANAGE_CHORES, "SCOPE_read")))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get(base + "/swaps")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base + "/swaps").with(TestTokens.jwtWith(someone, "SCOPE_read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(base + "/swaps").with(TestTokens.jwtWith(someone, Permissions.MANAGE_CHORES, "SCOPE_write")))
                .andExpect(status().isForbidden());
    }

    @Test
    void outsiderCannotUseTheHouseChores() throws Exception {
        Fixture f = houseWithThreeResidents();
        String choreId = createChore(f, "Varrer", "ONCE", f.ana(), order(f.ana(), f.bruno(), f.carla()));
        String outsider = registerUser("Fora");

        mockMvc.perform(get(f.chores() + "/swaps").with(TestTokens.jwtForUser(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(f.chore(choreId) + "/reopen").with(TestTokens.jwtForUser(outsider)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ create, frequencies, completions

    @Test
    void createDefaultsAndValidatesRotationOrder() throws Exception {
        Fixture f = houseWithThreeResidents();

        mockMvc.perform(post(f.chores())
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Lixo\",\"tipo\":\"ROTATIVA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.frequency").value("ONCE"))
                .andExpect(jsonPath("$.requiredCompletions").value(1))
                .andExpect(jsonPath("$.completionsThisCycle").value(0))
                .andExpect(jsonPath("$.linkedToGroceries").value(false))
                .andExpect(jsonPath("$.rotationOrder.length()").value(3))
                .andExpect(jsonPath("$.currentAssigneeId").value(f.ana()))
                .andExpect(jsonPath("$.effectiveAssigneeId").value(f.ana()))
                .andExpect(jsonPath("$.cycleNumber").value(1));

        mockMvc.perform(post(f.chores())
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Comprar gás\",\"choreType\":\"FIXED\",\"frequency\":\"BIWEEKLY\","
                                + "\"linkedToGroceries\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.frequency").value("BIWEEKLY"))
                .andExpect(jsonPath("$.requiredCompletions").value(1))
                .andExpect(jsonPath("$.linkedToGroceries").value(true));

        mockMvc.perform(post(f.chores())
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Varrer\",\"choreType\":\"ROTATIONAL\",\"rotationOrder\":"
                                + order(f.ana(), f.bruno()) + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("rotation order must match current house members"));
    }

    @Test
    void thriceNeedsThreeCompletionsOnlyByTheAssigneeAndCanBeUndone() throws Exception {
        Fixture f = houseWithThreeResidents();
        String choreId = createChore(f, "Louça", "THRICE", f.bruno(), order(f.ana(), f.bruno(), f.carla()));

        mockMvc.perform(put(f.chore(choreId) + "/complete").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the assignee can complete"));

        mockMvc.perform(put(f.chore(choreId) + "/complete").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.completionsThisCycle").value(1))
                .andExpect(jsonPath("$.requiredCompletions").value(3));
        complete(f, choreId, f.bruno());
        mockMvc.perform(put(f.chore(choreId) + "/complete").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completionsThisCycle").value(3));
        mockMvc.perform(put(f.chore(choreId) + "/complete").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("chore already completed for this cycle"));

        mockMvc.perform(put(f.chore(choreId) + "/uncomplete").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the assignee can complete"));
        mockMvc.perform(put(f.chore(choreId) + "/uncomplete").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.completionsThisCycle").value(2));
    }

    @Test
    void onTimeCompletionEarnsTwentyPointsOnceAtTheClose() throws Exception {
        Fixture f = houseWithThreeResidents();
        String choreId = createChore(f, "Banheiro", "ONCE", f.bruno(), order(f.ana(), f.bruno(), f.carla()));
        complete(f, choreId, f.bruno());

        assertTrue(choreCredits(f.bruno(), choreId).isEmpty(), "points only come with the close");
        close(f);

        List<PointLedgerEntry> credits = choreCredits(f.bruno(), choreId);
        assertEquals(1, credits.size());
        assertEquals(20, credits.get(0).getAmount());
        assertEquals(PointReason.CHORE_ON_TIME, credits.get(0).getReason());
        assertEquals("cycle-1", credits.get(0).getCycleKey());

        JsonNode chore = choreJson(f, choreId);
        assertEquals(f.carla(), chore.get("currentAssigneeId").asText());
        assertEquals("PENDING", chore.get("status").asText());
        assertEquals(2, chore.get("cycleNumber").asInt());
        assertEquals(f.ana(), chore.get("nextAssigneeId").asText());
    }

    @Test
    void reopenByAnotherResidentMakesItDelayedAndCancelsThePoints() throws Exception {
        Fixture f = houseWithThreeResidents();
        String choreId = createChore(f, "Cozinha", "ONCE", f.ana(), order(f.ana(), f.bruno(), f.carla()));

        mockMvc.perform(put(f.chore(choreId) + "/reopen").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("chore is not completed"));

        complete(f, choreId, f.ana());
        mockMvc.perform(put(f.chore(choreId) + "/reopen").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("assignee cannot reopen own chore"));
        mockMvc.perform(put(f.chore(choreId) + "/reopen").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELAYED"))
                .andExpect(jsonPath("$.delayedThisCycle").value(true))
                .andExpect(jsonPath("$.reopenedById").value(f.bruno()))
                .andExpect(jsonPath("$.reopenedByName").value("Bruno Morador"))
                .andExpect(jsonPath("$.completionsThisCycle").value(0));

        complete(f, choreId, f.ana());
        close(f);

        assertTrue(choreCredits(f.ana(), choreId).isEmpty(), "a reopened chore never earns the on-time points");
        JsonNode chore = choreJson(f, choreId);
        assertEquals(f.bruno(), chore.get("currentAssigneeId").asText());
        assertTrue(chore.get("reopenedById").isNull());
    }

    // ------------------------------------------------------------------ rotation order

    @Test
    void onlyTheAdminSetsTheRotationAndItMustBeExactlyTheResidents() throws Exception {
        Fixture f = houseWithThreeResidents();
        String choreId = createChore(f, "Quintal", "ONCE", f.ana(), order(f.ana(), f.bruno(), f.carla()));
        String rotation = f.chore(choreId) + "/rotation";

        mockMvc.perform(put(rotation).with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order\":" + order(f.ana(), f.carla(), f.bruno()) + "}"))
                .andExpect(status().isForbidden());

        for (String bad : List.of(order(f.ana(), f.carla()), order(f.ana(), f.carla(), f.carla()),
                order(f.ana(), f.bruno(), f.carla(), UUID.randomUUID().toString()))) {
            mockMvc.perform(put(rotation).with(TestTokens.jwtForUser(f.ana()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"order\":" + bad + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("rotation order must match current house members"));
        }

        mockMvc.perform(put(rotation).with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order\":" + order(f.ana(), f.carla(), f.bruno()) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rotationOrder[1]").value(f.carla()))
                .andExpect(jsonPath("$.nextAssigneeId").value(f.carla()));

        complete(f, choreId, f.ana());
        close(f);
        assertEquals(f.carla(), choreJson(f, choreId).get("currentAssigneeId").asText());
    }

    // ------------------------------------------------------------------ swaps

    @Test
    void acceptedSwapIsTemporaryCreditsWhoCoveredAndRotationFollowsTheOwner() throws Exception {
        Fixture f = houseWithThreeResidents();
        String rotation = order(f.ana(), f.bruno(), f.carla());
        String anaChore = createChore(f, "Lixo", "ONCE", f.ana(), rotation);
        String brunoChore = createChore(f, "Louça", "ONCE", f.bruno(), rotation);

        MvcResult created = mockMvc.perform(post(f.chore(anaChore) + "/swaps")
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetChoreId\":\"" + brunoChore + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.targetUserId").value(f.bruno()))
                .andReturn();
        String swapId = json(created).get("id").asText();

        assertEquals(swapId, choreJson(f, anaChore).get("pendingSwap").get("id").asText());
        assertEquals(swapId, choreJson(f, brunoChore).get("pendingSwaps").get(0).get("id").asText());
        mockMvc.perform(get(f.chores() + "/swaps").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requesterName").value("Ana Admin"));

        // Duplicate proposal for the same chores in this cycle.
        mockMvc.perform(post(f.chore(anaChore) + "/swaps")
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetChoreId\":\"" + brunoChore + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid chore swap"));

        mockMvc.perform(put(f.chores() + "/swaps/" + swapId + "/accept").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(f.chores() + "/swaps/" + swapId + "/accept").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        JsonNode swapped = choreJson(f, anaChore);
        assertEquals(f.ana(), swapped.get("currentAssigneeId").asText(), "the rotation slot does not change");
        assertEquals(f.bruno(), swapped.get("effectiveAssigneeId").asText());
        assertEquals(f.bruno(), swapped.get("temporaryAssigneeId").asText());
        assertTrue(swapped.get("pendingSwap").isNull());

        mockMvc.perform(put(f.chore(anaChore) + "/complete").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the assignee can complete"));
        complete(f, anaChore, f.bruno());
        complete(f, brunoChore, f.ana());
        close(f);

        assertEquals(1, choreCredits(f.bruno(), anaChore).size(), "+20 goes to who covered the chore");
        assertTrue(choreCredits(f.ana(), anaChore).isEmpty());
        assertEquals(1, choreCredits(f.ana(), brunoChore).size());

        JsonNode afterClose = choreJson(f, anaChore);
        assertEquals(f.bruno(), afterClose.get("currentAssigneeId").asText(), "rotation moves on from Ana's slot");
        assertTrue(afterClose.get("temporaryAssigneeId").isNull(), "the swap lasts one cycle only");
        assertEquals(f.carla(), choreJson(f, brunoChore).get("currentAssigneeId").asText());
    }

    @Test
    void rejectedAndInvalidSwapsAndPendingSwapsAreCancelledByTheClose() throws Exception {
        Fixture f = houseWithThreeResidents();
        String rotation = order(f.ana(), f.bruno(), f.carla());
        String anaChore = createChore(f, "Lixo", "ONCE", f.ana(), rotation);
        String anaOther = createChore(f, "Varanda", "ONCE", f.ana(), rotation);
        String carlaChore = createChore(f, "Sala", "ONCE", f.carla(), rotation);

        for (String body : List.of("{\"targetChoreId\":\"" + anaChore + "\"}",
                "{\"targetChoreId\":\"" + anaOther + "\"}",
                "{\"targetChoreId\":\"" + UUID.randomUUID() + "\"}",
                "{}")) {
            mockMvc.perform(post(f.chore(anaChore) + "/swaps")
                            .with(TestTokens.jwtForUser(f.ana()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid chore swap"));
        }
        // Bruno is not responsible for Ana's chore.
        mockMvc.perform(post(f.chore(anaChore) + "/swaps")
                        .with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetChoreId\":\"" + carlaChore + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid chore swap"));

        String rejected = json(mockMvc.perform(post(f.chore(anaChore) + "/swaps")
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetChoreId\":\"" + carlaChore + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        mockMvc.perform(put(f.chores() + "/swaps/" + rejected + "/reject").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(f.chores() + "/swaps/" + rejected + "/reject").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(put(f.chores() + "/swaps/" + rejected + "/accept").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isBadRequest());

        String pending = json(mockMvc.perform(post(f.chore(anaChore) + "/swaps")
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetChoreId\":\"" + carlaChore + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        complete(f, anaChore, f.ana());
        close(f);

        assertEquals(ChoreSwapStatus.CANCELLED, choreSwapRepository.findById(pending).orElseThrow().getStatus());
        mockMvc.perform(put(f.chores() + "/swaps/" + pending + "/accept").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid chore swap"));
    }

    // ------------------------------------------------------------------ manual close vs Sunday job, biweekly

    @Test
    void fridayManualCloseAndSundayJobOfTheSameIsoWeekDoNotRotateTwice() throws Exception {
        Fixture f = houseWithThreeResidents();
        String choreId = createChore(f, "Geladeira", "ONCE", f.ana(), order(f.ana(), f.bruno(), f.carla()));
        complete(f, choreId, f.ana());
        close(f);
        assertEquals(f.bruno(), choreJson(f, choreId).get("currentAssigneeId").asText());
        complete(f, choreId, f.bruno());

        clock.pin(SUNDAY_SAME_WEEK);
        choreService.rotateAllHousesScheduled();

        Chore sameWeek = choreRepository.findById(choreId).orElseThrow();
        assertEquals(f.bruno(), sameWeek.getCurrentAssigneeId(), "the job must skip a week the admin already closed");
        assertEquals("COMPLETED", sameWeek.getStatus().name());
        assertTrue(choreCredits(f.bruno(), choreId).isEmpty());

        clock.pin(NEXT_SUNDAY);
        choreService.rotateAllHousesScheduled();

        Chore nextWeek = choreRepository.findById(choreId).orElseThrow();
        assertEquals(f.carla(), nextWeek.getCurrentAssigneeId());
        assertEquals("2026-W49", nextWeek.getLastClosedWeek());
        assertEquals(1, choreCredits(f.bruno(), choreId).size());

        // Running the job again in the same week changes nothing.
        choreService.rotateAllHousesScheduled();
        assertEquals(f.carla(), choreRepository.findById(choreId).orElseThrow().getCurrentAssigneeId());
    }

    @Test
    void biweeklyChoreOnlyClosesRotatesAndCreditsOnTheSecondWeek() throws Exception {
        Fixture f = houseWithThreeResidents();
        String choreId = createChore(f, "Faxina geral", "BIWEEKLY", f.ana(), order(f.ana(), f.bruno(), f.carla()));
        complete(f, choreId, f.ana());

        clock.pin(SUNDAY_SAME_WEEK);
        choreService.rotateAllHousesScheduled();
        Chore firstWeek = choreRepository.findById(choreId).orElseThrow();
        assertEquals(f.ana(), firstWeek.getCurrentAssigneeId());
        assertEquals("COMPLETED", firstWeek.getStatus().name());
        assertEquals(1, firstWeek.getWeeksInCycle());
        assertTrue(choreCredits(f.ana(), choreId).isEmpty());

        clock.pin(NEXT_SUNDAY);
        choreService.rotateAllHousesScheduled();
        Chore secondWeek = choreRepository.findById(choreId).orElseThrow();
        assertEquals(f.bruno(), secondWeek.getCurrentAssigneeId());
        assertEquals("PENDING", secondWeek.getStatus().name());
        assertEquals(0, secondWeek.getWeeksInCycle());
        assertEquals(1, choreCredits(f.ana(), choreId).size());

        // Bruno's two weeks: not done by the end of them, the chore goes DELAYED and stays with him.
        clock.pin(SUNDAY_AFTER);
        choreService.rotateAllHousesScheduled();
        assertEquals("PENDING", choreRepository.findById(choreId).orElseThrow().getStatus().name());
        clock.pin(SUNDAY_AFTER.plusSeconds(7L * 24 * 3600));
        choreService.rotateAllHousesScheduled();
        Chore late = choreRepository.findById(choreId).orElseThrow();
        assertEquals("DELAYED", late.getStatus().name());
        assertEquals(f.bruno(), late.getCurrentAssigneeId());
    }

    // ------------------------------------------------------------------ resilience to former residents

    @Test
    void formerResidentsInTheRotationOrSwapAreIgnored() throws Exception {
        Fixture f = houseWithThreeResidents();
        String rotation = order(f.ana(), f.carla(), f.bruno());
        String anaChore = createChore(f, "Lixo", "ONCE", f.ana(), rotation);
        String carlaChore = createChore(f, "Sala", "ONCE", f.carla(), rotation);

        String swapId = json(mockMvc.perform(post(f.chore(anaChore) + "/swaps")
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetChoreId\":\"" + carlaChore + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        mockMvc.perform(put(f.chores() + "/swaps/" + swapId + "/accept").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk());
        assertEquals(f.carla(), choreJson(f, anaChore).get("effectiveAssigneeId").asText());

        // Carla moves out without the chores being touched (e.g. data from before the rotation existed).
        User carla = userRepository.findById(f.carla()).orElseThrow();
        carla.setHouseId(null);
        userRepository.save(carla);
        String dani = registerUser("Dani Nova");
        User newcomer = userRepository.findById(dani).orElseThrow();
        newcomer.setHouseId(f.houseId());
        userRepository.save(newcomer);

        JsonNode chore = choreJson(f, anaChore);
        assertEquals(f.ana(), chore.get("effectiveAssigneeId").asText(), "a temporary assignee who left is ignored");
        assertTrue(chore.get("temporaryAssigneeId").isNull());
        assertEquals(3, chore.get("rotationOrder").size());
        assertEquals(f.ana(), chore.get("rotationOrder").get(0).asText());
        assertEquals(f.bruno(), chore.get("rotationOrder").get(1).asText(), "the former resident is skipped");
        assertEquals(dani, chore.get("rotationOrder").get(2).asText(), "newcomers join at the end");
        assertEquals(f.bruno(), chore.get("nextAssigneeId").asText());

        complete(f, anaChore, f.ana());
        close(f);
        assertEquals(f.bruno(), choreJson(f, anaChore).get("currentAssigneeId").asText());
        assertEquals(1, choreCredits(f.ana(), anaChore).size());
    }
}
