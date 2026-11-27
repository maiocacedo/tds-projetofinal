package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.PointLedgerEntry;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import com.nossoteto.model.enums.SplitType;
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.PointLedgerRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import com.nossoteto.support.TestTokens;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseEnhancementsIntegrationTest {

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
        MutableClock mutableTestClock() {
            return new MutableClock();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ExpenseSplitRepository expenseSplitRepository;
    @Autowired private PointLedgerRepository pointLedgerRepository;
    @Autowired private MutableClock clock;

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    private record Fixture(String houseId, String ana, String bruno, String carla) {}

    private String registerUser(String name) throws Exception {
        String email = "expenses-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"senhaSegura123\"}"))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private Fixture houseWithThreeResidents() throws Exception {
        String ana = registerUser("Ana Pagadora");
        String bruno = registerUser("Bruno Devedor");
        String carla = registerUser("Carla Devedora");

        MvcResult house = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa das Contas " + UUID.randomUUID() + "\"}"))
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

    private JsonNode createExpense(String userId, String houseId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/houses/" + houseId + "/expenses")
                        .with(TestTokens.jwtForUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result);
    }

    private JsonNode createExpense(String userId, String houseId, String total, LocalDate dueDate) throws Exception {
        return createExpense(userId, houseId,
                "{\"title\":\"Conta de Luz\",\"totalAmount\":" + total + ",\"dueDate\":\"" + dueDate + "\"}");
    }

    private String splitIdOf(JsonNode expense, String userId) {
        for (JsonNode split : expense.get("splits")) {
            if (userId.equals(split.get("userId").asText())) {
                return split.get("id").asText();
            }
        }
        throw new AssertionError("no split for " + userId);
    }

    private void pay(String userId, String houseId, String expenseId) throws Exception {
        mockMvc.perform(put("/api/houses/" + houseId + "/expenses/" + expenseId + "/pay")
                        .with(TestTokens.jwtForUser(userId)))
                .andExpect(status().isOk());
    }

    private List<PointLedgerEntry> ledgerOf(String userId) {
        return pointLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void newEndpointsShouldRequireAuthenticationRoleAndScope() throws Exception {
        String base = "/api/houses/h1/expenses";
        String someone = UUID.randomUUID().toString();

        mockMvc.perform(get(base + "/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(base + "/e1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + "/e1")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(base + "/e1/splits/s1/confirm-received")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(base + "/e1/splits/s1/not-received")).andExpect(status().isUnauthorized());

        // scopes without ROLE_MANAGE_EXPENSES
        mockMvc.perform(get(base + "/summary").with(TestTokens.jwtWith(someone, "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(base + "/e1").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(TestTokens.jwtWith(someone, "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(base + "/e1").with(TestTokens.jwtWith(someone, "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(base + "/e1/splits/s1/confirm-received")
                        .with(TestTokens.jwtWith(someone, "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(base + "/e1/splits/s1/not-received")
                        .with(TestTokens.jwtWith(someone, "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());

        // role but wrong scope
        mockMvc.perform(get(base + "/summary").with(TestTokens.jwtWith(someone, Permissions.MANAGE_EXPENSES, "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(base + "/e1").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(TestTokens.jwtWith(someone, Permissions.MANAGE_EXPENSES, "SCOPE_read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(base + "/e1").with(TestTokens.jwtWith(someone, Permissions.MANAGE_EXPENSES, "SCOPE_read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(base + "/e1/splits/s1/confirm-received")
                        .with(TestTokens.jwtWith(someone, Permissions.MANAGE_EXPENSES, "SCOPE_read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(base + "/e1/splits/s1/not-received")
                        .with(TestTokens.jwtWith(someone, Permissions.MANAGE_EXPENSES, "SCOPE_read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void payerShareIsBornPaidAndListingExposesPayerAndNames() throws Exception {
        Fixture f = houseWithThreeResidents();
        JsonNode created = createExpense(f.ana(), f.houseId(), "300.00", LocalDate.of(2099, 1, 10));
        String expenseId = created.get("id").asText();

        mockMvc.perform(get("/api/houses/" + f.houseId() + "/expenses").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + expenseId + "')].paidBy.id").value(f.ana()))
                .andExpect(jsonPath("$[?(@.id=='" + expenseId + "')].paidBy.name").value("Ana Pagadora"))
                .andExpect(jsonPath("$[?(@.id=='" + expenseId + "')].createdById").value(f.ana()))
                .andExpect(jsonPath("$[?(@.id=='" + expenseId + "')].recurrenceActive").value(false));

        ExpenseSplit anaSplit = expenseSplitRepository.findById(splitIdOf(created, f.ana())).orElseThrow();
        ExpenseSplit brunoSplit = expenseSplitRepository.findById(splitIdOf(created, f.bruno())).orElseThrow();
        assertTrue(anaSplit.getIsPaid());
        assertNotNull(anaSplit.getPaidAt());
        assertNotNull(anaSplit.getReceivedConfirmedAt());
        assertFalse(brunoSplit.getIsPaid());
        assertNull(brunoSplit.getPaidAt());

        for (JsonNode split : created.get("splits")) {
            assertFalse(split.get("userName").asText().isBlank());
        }
        assertTrue(ledgerOf(f.ana()).isEmpty(), "the payer earns no points for their own share");

        Expense stored = expenseRepository.findById(expenseId).orElseThrow();
        assertEquals(f.ana(), stored.getPaidById());
        assertEquals(f.ana(), stored.getCreatedById());
        assertFalse(stored.getRecurrenceActive());
        assertNull(stored.getRecurrenceSourceId());
    }

    @Test
    void createWithAnotherPayerAndActiveRecurrenceShouldPersistBoth() throws Exception {
        Fixture f = houseWithThreeResidents();
        clock.pin(Instant.parse("2026-12-01T02:00:00Z")); // still 2026-11-30 in America/Sao_Paulo

        // Due today in Sao Paulo: the recurrence month follows Sao Paulo (2026-11), not UTC (2026-12).
        JsonNode created = createExpense(f.ana(), f.houseId(),
                "{\"title\":\"Internet\",\"totalAmount\":90,\"dueDate\":\"2026-11-30\","
                        + "\"paidById\":\"" + f.bruno() + "\",\"recurringDay\":31,\"recurrenceActive\":true}");

        assertEquals(f.bruno(), created.get("paidBy").get("id").asText());
        assertEquals("Bruno Devedor", created.get("paidBy").get("name").asText());
        Expense stored = expenseRepository.findById(created.get("id").asText()).orElseThrow();
        assertEquals(f.ana(), stored.getCreatedById());
        assertEquals(31, stored.getRecurringDay());
        assertEquals(stored.getId(), stored.getRecurrenceSourceId());
        assertEquals("2026-11", stored.getRecurrenceMonth());
        assertTrue(expenseSplitRepository.findById(splitIdOf(created, f.bruno())).orElseThrow().getIsPaid());
        assertFalse(expenseSplitRepository.findById(splitIdOf(created, f.ana())).orElseThrow().getIsPaid());

        mockMvc.perform(post("/api/houses/" + f.houseId() + "/expenses")
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"totalAmount\":10,\"dueDate\":\"2026-12-05\",\"paidById\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("payer must be a current house member"));

        mockMvc.perform(post("/api/houses/" + f.houseId() + "/expenses")
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"totalAmount\":10,\"dueDate\":\"2026-12-05\",\"recurringDay\":32}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("recurringDay must be between 1 and 31"));
    }

    @Test
    void payOnDueDateLateEveningInSaoPauloEarnsTenAndAfterDueDateEarnsNothing() throws Exception {
        Fixture f = houseWithThreeResidents();
        LocalDate dueDate = LocalDate.of(2026, 11, 26);
        clock.pin(Instant.parse("2026-11-25T15:00:00Z"));
        JsonNode created = createExpense(f.ana(), f.houseId(), "300.00", dueDate);
        String expenseId = created.get("id").asText();
        String brunoSplitId = splitIdOf(created, f.bruno());

        // 2026-11-27T02:30Z == 2026-11-26 23:30 in America/Sao_Paulo: still on the due date
        clock.pin(Instant.parse("2026-11-27T02:30:00Z"));
        pay(f.bruno(), f.houseId(), expenseId);

        List<PointLedgerEntry> brunoLedger = ledgerOf(f.bruno());
        assertEquals(1, brunoLedger.size());
        assertEquals(PointReason.EXPENSE_ON_TIME, brunoLedger.get(0).getReason());
        assertEquals(10, brunoLedger.get(0).getAmount());
        assertEquals(PointSourceType.EXPENSE_SPLIT, brunoLedger.get(0).getSourceType());
        assertEquals(brunoSplitId, brunoLedger.get(0).getSourceId());
        assertEquals("Conta de Luz", brunoLedger.get(0).getDescription());
        var firstPaidAt = expenseSplitRepository.findById(brunoSplitId).orElseThrow().getPaidAt();
        assertEquals(java.time.LocalDateTime.of(2026, 11, 27, 2, 30), firstPaidAt);

        // paying again neither overwrites paidAt nor credits twice
        clock.pin(Instant.parse("2026-11-27T02:45:00Z"));
        pay(f.bruno(), f.houseId(), expenseId);
        assertEquals(1, ledgerOf(f.bruno()).size());
        assertEquals(firstPaidAt, expenseSplitRepository.findById(brunoSplitId).orElseThrow().getPaidAt());

        // 2026-11-27T03:30Z == 2026-11-27 00:30 in America/Sao_Paulo: one day late
        clock.pin(Instant.parse("2026-11-27T03:30:00Z"));
        pay(f.carla(), f.houseId(), expenseId);
        assertTrue(ledgerOf(f.carla()).isEmpty());
        assertTrue(expenseSplitRepository.findById(splitIdOf(created, f.carla())).orElseThrow().getIsPaid());
    }

    @Test
    void confirmReceivedAndNotReceivedShouldBeRestrictedToPayerAndReversePoints() throws Exception {
        Fixture f = houseWithThreeResidents();
        JsonNode created = createExpense(f.ana(), f.houseId(), "300.00", LocalDate.of(2099, 1, 10));
        String expenseId = created.get("id").asText();
        String brunoSplitId = splitIdOf(created, f.bruno());
        String carlaSplitId = splitIdOf(created, f.carla());
        String splitsUrl = "/api/houses/" + f.houseId() + "/expenses/" + expenseId + "/splits/";

        pay(f.bruno(), f.houseId(), expenseId);
        assertEquals(10, ledgerOf(f.bruno()).get(0).getAmount());

        mockMvc.perform(put(splitsUrl + brunoSplitId + "/confirm-received").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the payer can confirm payment"));
        mockMvc.perform(put(splitsUrl + brunoSplitId + "/not-received").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the payer can confirm payment"));
        mockMvc.perform(put(splitsUrl + carlaSplitId + "/confirm-received").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("split is not marked as paid"));
        mockMvc.perform(put(splitsUrl + carlaSplitId + "/not-received").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("split is not marked as paid"));

        mockMvc.perform(put(splitsUrl + brunoSplitId + "/confirm-received").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits[?(@.id=='" + brunoSplitId + "')].receivedConfirmedAt").isNotEmpty());
        assertNotNull(expenseSplitRepository.findById(brunoSplitId).orElseThrow().getReceivedConfirmedAt());

        mockMvc.perform(put(splitsUrl + brunoSplitId + "/not-received").with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits[?(@.id=='" + brunoSplitId + "')].isPaid").value(false));

        ExpenseSplit reopened = expenseSplitRepository.findById(brunoSplitId).orElseThrow();
        assertFalse(reopened.getIsPaid());
        assertNull(reopened.getPaidAt());
        assertNull(reopened.getReceivedConfirmedAt());
        assertEquals(f.ana(), reopened.getReopenedById());
        assertNotNull(reopened.getReopenedAt());
        assertTrue(ledgerOf(f.bruno()).isEmpty(), "not-received must reverse the +10");

        // paying again after the reopen earns the points again
        pay(f.bruno(), f.houseId(), expenseId);
        assertEquals(1, ledgerOf(f.bruno()).size());
    }

    @Test
    void updateAndDeleteShouldBeCreatorOnlyAndBlockedOnceSomeoneElsePaid() throws Exception {
        Fixture f = houseWithThreeResidents();
        JsonNode created = createExpense(f.ana(), f.houseId(), "300.00", LocalDate.of(2099, 1, 10));
        String expenseUrl = "/api/houses/" + f.houseId() + "/expenses/" + created.get("id").asText();

        mockMvc.perform(put(expenseUrl).with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hack\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the creator can modify this expense"));
        mockMvc.perform(delete(expenseUrl).with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the creator can modify this expense"));

        // only the payer's own share is paid: editing is still allowed and shares are recalculated
        mockMvc.perform(put(expenseUrl).with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Luz de novembro\",\"totalAmount\":90,\"dueDate\":\"2099-02-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Luz de novembro"))
                .andExpect(jsonPath("$.totalAmount").value(90.00))
                .andExpect(jsonPath("$.dueDate").value("2099-02-10"));
        for (ExpenseSplit split : expenseSplitRepository.findByExpenseId(created.get("id").asText())) {
            assertEquals(0, new BigDecimal("30.00").compareTo(split.getAmountDue()));
            assertEquals(split.getUserId().equals(f.ana()), split.getIsPaid());
        }

        pay(f.bruno(), f.houseId(), created.get("id").asText());

        mockMvc.perform(put(expenseUrl).with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalAmount\":120}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("expense already has payments"));
        mockMvc.perform(delete(expenseUrl).with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("expense already has payments"));

        JsonNode untouched = createExpense(f.ana(), f.houseId(), "60.00", LocalDate.of(2099, 1, 10));
        String untouchedId = untouched.get("id").asText();
        mockMvc.perform(delete("/api/houses/" + f.houseId() + "/expenses/" + untouchedId)
                        .with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isNoContent());
        assertTrue(expenseRepository.findById(untouchedId).isEmpty());
        assertTrue(expenseSplitRepository.findByExpenseId(untouchedId).isEmpty());
    }

    @Test
    void proposeSplitShouldAcceptZeroDespitePayerPaidShareAndRejectWrongSum() throws Exception {
        Fixture f = houseWithThreeResidents();
        JsonNode created = createExpense(f.ana(), f.houseId(), "300.00", LocalDate.of(2099, 1, 10));
        String proposeUrl = "/api/houses/" + f.houseId() + "/expenses/" + created.get("id").asText() + "/propose-split";

        mockMvc.perform(post(proposeUrl).with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDivisao\":\"VALOR\",\"divisoes\":["
                                + "{\"usuarioId\":\"" + f.ana() + "\",\"valorOuPorcentagem\":150},"
                                + "{\"usuarioId\":\"" + f.bruno() + "\",\"valorOuPorcentagem\":150},"
                                + "{\"usuarioId\":\"" + f.carla() + "\",\"valorOuPorcentagem\":0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType").value(SplitType.CUSTOM_PENDING.name()));

        mockMvc.perform(post(proposeUrl).with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDivisao\":\"VALOR\",\"divisoes\":["
                                + "{\"usuarioId\":\"" + f.ana() + "\",\"valorOuPorcentagem\":100},"
                                + "{\"usuarioId\":\"" + f.bruno() + "\",\"valorOuPorcentagem\":100},"
                                + "{\"usuarioId\":\"" + f.carla() + "\",\"valorOuPorcentagem\":50}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("split must add up to the total"));

        mockMvc.perform(post(proposeUrl).with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDivisao\":\"PORCENTAGEM\",\"divisoes\":["
                                + "{\"usuarioId\":\"" + f.ana() + "\",\"valorOuPorcentagem\":50},"
                                + "{\"usuarioId\":\"" + f.bruno() + "\",\"valorOuPorcentagem\":30}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("split must add up to the total"));
    }

    @Test
    void summaryShouldShowDebtsCreditsAndFormerResidentWithName() throws Exception {
        Fixture f = houseWithThreeResidents();
        createExpense(f.ana(), f.houseId(), "300.00", LocalDate.of(2099, 1, 10));
        createExpense(f.carla(), f.houseId(), "60.00", LocalDate.of(2099, 1, 15));

        // Bruno leaves the house with his shares still open: debts stay
        mockMvc.perform(post("/api/houses/" + f.houseId() + "/leave").with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isNoContent());

        // a legacy expense (no payer recorded) must not break the summary
        Expense legacy = new Expense();
        legacy.setId(UUID.randomUUID().toString());
        legacy.setHouseId(f.houseId());
        legacy.setTitle("Despesa antiga");
        legacy.setTotalAmount(new BigDecimal("10.00"));
        legacy.setDueDate(LocalDate.of(2026, 1, 10));
        expenseRepository.save(legacy);
        ExpenseSplit legacySplit = new ExpenseSplit();
        legacySplit.setId(UUID.randomUUID().toString());
        legacySplit.setExpenseId(legacy.getId());
        legacySplit.setUserId(f.ana());
        legacySplit.setAmountDue(new BigDecimal("10.00"));
        legacySplit.setIsPaid(false);
        expenseSplitRepository.save(legacySplit);

        String summaryUrl = "/api/houses/" + f.houseId() + "/expenses/summary";
        mockMvc.perform(get(summaryUrl).with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owedToMe.length()").value(2))
                .andExpect(jsonPath("$.owedToMe[?(@.debtorId=='" + f.bruno() + "')].debtorName").value("Bruno Devedor"))
                .andExpect(jsonPath("$.owedToMe[?(@.debtorId=='" + f.bruno() + "')].amount").value(100.00))
                .andExpect(jsonPath("$.owedToMe[?(@.debtorId=='" + f.carla() + "')].amount").value(100.00))
                .andExpect(jsonPath("$.myDebts.length()").value(1))
                .andExpect(jsonPath("$.myDebts[0].creditorId").value(f.carla()))
                .andExpect(jsonPath("$.myDebts[0].creditorName").value("Carla Devedora"))
                .andExpect(jsonPath("$.myDebts[0].amount").value(20.00))
                .andExpect(jsonPath("$.members[?(@.userId=='" + f.bruno() + "')].name").value("Bruno Devedor"))
                .andExpect(jsonPath("$.members[?(@.userId=='" + f.bruno() + "')].openAmount").value(120.00))
                .andExpect(jsonPath("$.members[?(@.userId=='" + f.ana() + "')].paidCount").value(1))
                .andExpect(jsonPath("$.members[?(@.userId=='" + f.ana() + "')].totalCount").value(3))
                .andExpect(jsonPath("$.members[?(@.userId=='" + f.ana() + "')].openAmount").value(30.00));

        mockMvc.perform(get(summaryUrl).with(TestTokens.jwtForUser(f.bruno())))
                .andExpect(status().isForbidden());
    }

    private void voteApprove(String userId, String houseId, String expenseId) throws Exception {
        mockMvc.perform(post("/api/houses/" + houseId + "/expenses/" + expenseId + "/vote-split")
                        .with(TestTokens.jwtForUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voto\":\"APROVAR\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void approvedZeroShareShouldNotBePayableNorShowUpInSummaryNorBlockTheCreator() throws Exception {
        Fixture f = houseWithThreeResidents();
        JsonNode created = createExpense(f.ana(), f.houseId(), "300.00", LocalDate.of(2099, 1, 10));
        String expenseId = created.get("id").asText();
        String expenseUrl = "/api/houses/" + f.houseId() + "/expenses/" + expenseId;

        mockMvc.perform(post(expenseUrl + "/propose-split").with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDivisao\":\"VALOR\",\"divisoes\":["
                                + "{\"usuarioId\":\"" + f.ana() + "\",\"valorOuPorcentagem\":150},"
                                + "{\"usuarioId\":\"" + f.bruno() + "\",\"valorOuPorcentagem\":150},"
                                + "{\"usuarioId\":\"" + f.carla() + "\",\"valorOuPorcentagem\":0}]}"))
                .andExpect(status().isOk());
        for (String voter : List.of(f.ana(), f.bruno(), f.carla())) {
            voteApprove(voter, f.houseId(), expenseId);
        }
        assertEquals(SplitType.CUSTOM_APPROVED, expenseRepository.findById(expenseId).orElseThrow().getSplitType());

        mockMvc.perform(put(expenseUrl + "/pay").with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("split has nothing to pay"));
        assertTrue(ledgerOf(f.carla()).isEmpty());
        ExpenseSplit carlaSplit = expenseSplitRepository.findByExpenseIdAndUserId(expenseId, f.carla()).orElseThrow();
        assertFalse(carlaSplit.getIsPaid());

        String summaryUrl = "/api/houses/" + f.houseId() + "/expenses/summary";
        mockMvc.perform(get(summaryUrl).with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owedToMe.length()").value(1))
                .andExpect(jsonPath("$.owedToMe[0].debtorId").value(f.bruno()))
                .andExpect(jsonPath("$.owedToMe[0].amount").value(150.00))
                .andExpect(jsonPath("$.members[?(@.userId=='" + f.carla() + "')].paidCount").value(0))
                .andExpect(jsonPath("$.members[?(@.userId=='" + f.carla() + "')].totalCount").value(0))
                .andExpect(jsonPath("$.members[?(@.userId=='" + f.carla() + "')].openAmount").value(0.00));
        mockMvc.perform(get(summaryUrl).with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myDebts.length()").value(0));

        mockMvc.perform(put(expenseUrl).with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Luz ajustada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Luz ajustada"));
        mockMvc.perform(delete(expenseUrl).with(TestTokens.jwtForUser(f.ana())))
                .andExpect(status().isNoContent());
    }

    @Test
    void percentageSplitShouldKeepZeroPercentPayerAtZeroAndNeverGoNegative() throws Exception {
        Fixture f = houseWithThreeResidents();
        JsonNode created = createExpense(f.ana(), f.houseId(), "50.01", LocalDate.of(2099, 1, 10));
        String proposeUrl = "/api/houses/" + f.houseId() + "/expenses/" + created.get("id").asText() + "/propose-split";

        mockMvc.perform(post(proposeUrl).with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDivisao\":\"PORCENTAGEM\",\"divisoes\":["
                                + "{\"usuarioId\":\"" + f.ana() + "\",\"valorOuPorcentagem\":0},"
                                + "{\"usuarioId\":\"" + f.bruno() + "\",\"valorOuPorcentagem\":50},"
                                + "{\"usuarioId\":\"" + f.carla() + "\",\"valorOuPorcentagem\":50}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits[?(@.userId=='" + f.ana() + "')].proposedAmountDue").value(0.00))
                .andExpect(jsonPath("$.splits[?(@.userId=='" + f.bruno() + "')].proposedAmountDue").value(25.01))
                .andExpect(jsonPath("$.splits[?(@.userId=='" + f.carla() + "')].proposedAmountDue").value(25.00));
    }

    @Test
    void createAndUpdateShouldRejectZeroRoundedTotalAndActiveRecurrenceWithoutDay() throws Exception {
        Fixture f = houseWithThreeResidents();
        String expensesUrl = "/api/houses/" + f.houseId() + "/expenses";

        mockMvc.perform(post(expensesUrl).with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Centavo\",\"totalAmount\":0.004,\"dueDate\":\"2099-01-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Expense total amount must be greater than zero"));
        mockMvc.perform(post(expensesUrl).with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Aluguel\",\"totalAmount\":900,\"dueDate\":\"2099-01-10\",\"recurrenceActive\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("recurringDay is required when recurrence is active"));

        JsonNode created = createExpense(f.ana(), f.houseId(), "90.00", LocalDate.of(2099, 1, 10));
        String expenseUrl = expensesUrl + "/" + created.get("id").asText();
        mockMvc.perform(put(expenseUrl).with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalAmount\":0.004}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Expense total amount must be greater than zero"));
        mockMvc.perform(put(expenseUrl).with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recurrenceActive\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("recurringDay is required when recurrence is active"));
        Expense untouched = expenseRepository.findById(created.get("id").asText()).orElseThrow();
        assertEquals(0, new BigDecimal("90.00").compareTo(untouched.getTotalAmount()));
        assertFalse(Boolean.TRUE.equals(untouched.getRecurrenceActive()));
    }
}
