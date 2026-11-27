package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.SplitType;
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.PointLedgerRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import com.nossoteto.service.ExpenseRecurrenceService;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseRecurrenceIntegrationTest {

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
        MutableClock recurrenceTestClock() {
            return new MutableClock();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ExpenseSplitRepository expenseSplitRepository;
    @Autowired private PointLedgerRepository pointLedgerRepository;
    @Autowired private ExpenseRecurrenceService recurrenceService;
    @Autowired private MutableClock clock;

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    /** Ana created the house (admin); Bruno and Carla are plain residents. */
    private record Fixture(String houseId, String ana, String bruno, String carla) {}

    private String registerUser(String name) throws Exception {
        String email = "recurrence-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"senhaSegura123\"}"))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private Fixture houseWithThreeResidents() throws Exception {
        String ana = registerUser("Ana Admin");
        String bruno = registerUser("Bruno Criador");
        String carla = registerUser("Carla Moradora");

        MvcResult house = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa Recorrente " + UUID.randomUUID() + "\"}"))
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

    private String createExpense(String userId, String houseId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/houses/" + houseId + "/expenses")
                        .with(TestTokens.jwtForUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private String createPlainExpense(String userId, String houseId, LocalDate dueDate) throws Exception {
        return createExpense(userId, houseId,
                "{\"title\":\"Aluguel\",\"totalAmount\":300,\"dueDate\":\"" + dueDate + "\"}");
    }

    private String recurrenceUrl(String houseId, String expenseId) {
        return "/api/houses/" + houseId + "/expenses/" + expenseId + "/recurrence";
    }

    private List<Expense> occurrencesOf(String houseId, String templateId) {
        return expenseRepository.findByHouseIdOrderByDueDateAsc(houseId).stream()
                .filter(e -> templateId.equals(e.getRecurrenceSourceId()) && !templateId.equals(e.getId()))
                .toList();
    }

    @Test
    void recurrenceEndpointShouldRequireAuthenticationRoleAndScope() throws Exception {
        String url = recurrenceUrl("h1", "e1");
        String body = "{\"active\":true,\"day\":5}";
        String someone = UUID.randomUUID().toString();

        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(TestTokens.jwtWith(someone, "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(TestTokens.jwtWith(someone, Permissions.MANAGE_EXPENSES, "SCOPE_read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatorAndAdminCanChangeRecurrenceAfterPaymentsButOtherResidentsCannot() throws Exception {
        Fixture f = houseWithThreeResidents();
        String expenseId = createPlainExpense(f.bruno(), f.houseId(), LocalDate.of(2099, 1, 10));
        mockMvc.perform(put("/api/houses/" + f.houseId() + "/expenses/" + expenseId + "/pay")
                        .with(TestTokens.jwtForUser(f.carla())))
                .andExpect(status().isOk());

        // Editing is blocked once someone paid, the recurrence switch is not.
        mockMvc.perform(put("/api/houses/" + f.houseId() + "/expenses/" + expenseId)
                        .with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recurringDay\":10,\"recurrenceActive\":true}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put(recurrenceUrl(f.houseId(), expenseId))
                        .with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"day\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceActive").value(true))
                .andExpect(jsonPath("$.recurringDay").value(10))
                .andExpect(jsonPath("$.paidBy.name").value("Bruno Criador"));

        mockMvc.perform(put(recurrenceUrl(f.houseId(), expenseId))
                        .with(TestTokens.jwtForUser(f.carla()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(recurrenceUrl(f.houseId(), expenseId))
                        .with(TestTokens.jwtForUser(f.ana()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recurrenceActive").value(false));

        Expense stored = expenseRepository.findById(expenseId).orElseThrow();
        assertFalse(stored.getRecurrenceActive());
        assertEquals(10, stored.getRecurringDay());
        assertEquals(expenseId, stored.getRecurrenceSourceId());
        assertEquals("2099-01", stored.getRecurrenceMonth());

        for (String body : List.of("{\"active\":true,\"day\":0}", "{\"active\":true,\"day\":32}",
                "{\"active\":true}", "{\"day\":5}")) {
            mockMvc.perform(put(recurrenceUrl(f.houseId(), expenseId))
                            .with(TestTokens.jwtForUser(f.bruno()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put(recurrenceUrl(f.houseId(), UUID.randomUUID().toString()))
                        .with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"day\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generatedOccurrenceCannotBecomeRecurringEitherWay() throws Exception {
        Fixture f = houseWithThreeResidents();
        clock.pin(Instant.parse("2026-10-10T15:00:00Z"));
        String templateId = createExpense(f.bruno(), f.houseId(),
                "{\"title\":\"Internet\",\"totalAmount\":90,\"dueDate\":\"2026-10-15\",\"recurringDay\":15,\"recurrenceActive\":true}");

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 11, 15));
        List<Expense> occurrences = occurrencesOf(f.houseId(), templateId);
        assertEquals(1, occurrences.size());
        String childId = occurrences.get(0).getId();

        mockMvc.perform(put(recurrenceUrl(f.houseId(), childId))
                        .with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"day\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("cannot change recurrence of a generated occurrence"));
        mockMvc.perform(put("/api/houses/" + f.houseId() + "/expenses/" + childId)
                        .with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recurringDay\":5,\"recurrenceActive\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("cannot change recurrence of a generated occurrence"));
        assertFalse(expenseRepository.findById(childId).orElseThrow().getRecurrenceActive());
    }

    @Test
    void sameMonthOriginalIsNotDuplicatedAndRunningTwiceGeneratesOnlyOneOccurrence() throws Exception {
        Fixture f = houseWithThreeResidents();
        clock.pin(Instant.parse("2026-11-03T15:00:00Z"));
        String templateId = createExpense(f.bruno(), f.houseId(),
                "{\"title\":\"Internet\",\"totalAmount\":90,\"dueDate\":\"2026-11-05\",\"recurringDay\":5,\"recurrenceActive\":true}");

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 11, 5));
        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 11, 20));
        assertTrue(occurrencesOf(f.houseId(), templateId).isEmpty(), "November is the original itself");

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 12, 5));
        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 12, 5));
        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 12, 6));

        List<Expense> occurrences = occurrencesOf(f.houseId(), templateId);
        assertEquals(1, occurrences.size());
        assertEquals("2026-12", occurrences.get(0).getRecurrenceMonth());
        assertEquals(LocalDate.of(2026, 12, 5), occurrences.get(0).getDueDate());
    }

    @Test
    void expenseCreatedOnNov26DueDec5IsNotDuplicatedByTheJob() throws Exception {
        Fixture f = houseWithThreeResidents();
        clock.pin(Instant.parse("2026-11-26T15:00:00Z"));
        String templateId = createExpense(f.bruno(), f.houseId(),
                "{\"title\":\"Internet\",\"totalAmount\":90,\"dueDate\":\"2026-12-05\",\"recurringDay\":5,\"recurrenceActive\":true}");
        assertEquals("2026-12", expenseRepository.findById(templateId).orElseThrow().getRecurrenceMonth());

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 11, 27));
        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 12, 5));
        assertTrue(occurrencesOf(f.houseId(), templateId).isEmpty());

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2027, 1, 5));
        List<Expense> occurrences = occurrencesOf(f.houseId(), templateId);
        assertEquals(1, occurrences.size());
        assertEquals(LocalDate.of(2027, 1, 5), occurrences.get(0).getDueDate());
    }

    @Test
    void activatingThroughTheEndpointWithANextMonthDueDateAlsoCoversThatMonth() throws Exception {
        Fixture f = houseWithThreeResidents();
        clock.pin(Instant.parse("2026-11-26T15:00:00Z"));
        String expenseId = createPlainExpense(f.bruno(), f.houseId(), LocalDate.of(2026, 12, 5));

        mockMvc.perform(put(recurrenceUrl(f.houseId(), expenseId))
                        .with(TestTokens.jwtForUser(f.bruno()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"day\":5}"))
                .andExpect(status().isOk());
        assertEquals("2026-12", expenseRepository.findById(expenseId).orElseThrow().getRecurrenceMonth());

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 12, 5));
        assertTrue(occurrencesOf(f.houseId(), expenseId).isEmpty());
    }

    @Test
    void dayThirtyOneFollowsTheLengthOfEachMonth() throws Exception {
        Fixture f = houseWithThreeResidents();
        clock.pin(Instant.parse("2026-01-10T15:00:00Z"));
        String templateId = createExpense(f.bruno(), f.houseId(),
                "{\"title\":\"Condominio\",\"totalAmount\":90,\"dueDate\":\"2026-01-31\",\"recurringDay\":31,\"recurrenceActive\":true}");

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 2, 27));
        assertTrue(occurrencesOf(f.houseId(), templateId).isEmpty());
        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 2, 28));
        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 4, 30));
        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2028, 2, 29));

        List<LocalDate> dueDates = occurrencesOf(f.houseId(), templateId).stream()
                .map(Expense::getDueDate)
                .toList();
        assertEquals(List.of(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 4, 30), LocalDate.of(2028, 2, 29)),
                dueDates);
    }

    @Test
    void occurrenceSplitsEquallyAmongCurrentResidentsWithPayerShareSettledAndNoPoints() throws Exception {
        Fixture f = houseWithThreeResidents();
        clock.pin(Instant.parse("2026-10-10T15:00:00Z"));
        String templateId = createExpense(f.bruno(), f.houseId(),
                "{\"title\":\"Luz\",\"totalAmount\":100,\"dueDate\":\"2026-10-15\",\"recurringDay\":15,"
                        + "\"recurrenceActive\":true,\"paidById\":\"" + f.ana() + "\"}");

        // The template carries a custom split and a former resident; neither is copied.
        Expense template = expenseRepository.findById(templateId).orElseThrow();
        template.setSplitType(SplitType.CUSTOM_APPROVED);
        expenseRepository.save(template);
        User carla = userRepository.findById(f.carla()).orElseThrow();
        carla.setHouseId(null);
        userRepository.save(carla);
        String dani = registerUser("Dani Nova");
        User daniUser = userRepository.findById(dani).orElseThrow();
        daniUser.setHouseId(f.houseId());
        userRepository.save(daniUser);

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 11, 20));

        List<Expense> occurrences = occurrencesOf(f.houseId(), templateId);
        assertEquals(1, occurrences.size());
        Expense child = occurrences.get(0);
        assertEquals(SplitType.EQUAL, child.getSplitType());
        assertEquals(f.ana(), child.getPaidById());
        assertEquals(f.bruno(), child.getCreatedById());
        assertEquals(LocalDate.of(2026, 11, 15), child.getDueDate());
        assertFalse(child.getRecurrenceActive());
        assertNull(child.getRecurringDay());
        assertNull(child.getAttachmentUrl());

        Map<String, ExpenseSplit> byUser = expenseSplitRepository.findByExpenseId(child.getId()).stream()
                .collect(Collectors.toMap(ExpenseSplit::getUserId, s -> s));
        assertEquals(java.util.Set.of(f.ana(), f.bruno(), dani), byUser.keySet());
        BigDecimal sum = byUser.values().stream().map(ExpenseSplit::getAmountDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100.00").compareTo(sum));
        assertEquals(0, new BigDecimal("33.34").compareTo(byUser.get(f.ana()).getAmountDue()));
        assertTrue(byUser.get(f.ana()).getIsPaid());
        assertTrue(byUser.get(f.ana()).getReceivedConfirmedAt() != null);
        assertFalse(byUser.get(f.bruno()).getIsPaid());
        assertFalse(byUser.get(dani).getIsPaid());
        assertTrue(pointLedgerRepository.findByUserIdOrderByCreatedAtDesc(f.ana()).isEmpty());
    }

    @Test
    void templateIsPausedWhenThePayerNoLongerLivesInTheHouse() throws Exception {
        Fixture f = houseWithThreeResidents();
        clock.pin(Instant.parse("2026-10-10T15:00:00Z"));
        String templateId = createExpense(f.ana(), f.houseId(),
                "{\"title\":\"Gas\",\"totalAmount\":60,\"dueDate\":\"2026-10-15\",\"recurringDay\":15,"
                        + "\"recurrenceActive\":true,\"paidById\":\"" + f.carla() + "\"}");
        User carla = userRepository.findById(f.carla()).orElseThrow();
        carla.setHouseId(null);
        userRepository.save(carla);

        recurrenceService.processRecurringExpensesForDate(LocalDate.of(2026, 11, 15));

        assertTrue(occurrencesOf(f.houseId(), templateId).isEmpty());
        Expense template = expenseRepository.findById(templateId).orElseThrow();
        assertFalse(template.getRecurrenceActive());
        assertEquals(templateId, template.getRecurrenceSourceId());
    }
}
