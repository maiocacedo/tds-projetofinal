package com.nossoteto.controller;

import com.nossoteto.dto.CreateExpenseRequest;
import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.ExpenseSummaryResponse;
import com.nossoteto.dto.UpdateExpenseRequest;
import com.nossoteto.dto.UserRefDto;
import com.nossoteto.dto.ProposeSplitRequest;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ConflictException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.model.enums.SplitType;
import com.nossoteto.service.ExpenseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseService expenseService;

    private UsernamePasswordAuthenticationToken auth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    }

    @Test
    void createExpenseShouldAcceptPortugueseAliasesAndReturn201() throws Exception {
        ExpenseResponse response = new ExpenseResponse();
        response.setId("exp-1");
        response.setHouseId("house-1");
        response.setTitle("Aluguel");
        response.setTotalAmount(new BigDecimal("2500.00"));
        response.setDueDate(LocalDate.of(2026, 11, 5));
        response.setHasAttachment(true);
        response.setSplitType(SplitType.EQUAL);

        when(expenseService.createExpense(eq("u1"), eq("house-1"), any(CreateExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/houses/house-1/expenses")
                        .principal(auth("u1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Aluguel\",\"valorTotal\":2500.00,\"vencimento\":\"2026-11-05\",\"anexoBase64\":\"YWJj\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("exp-1"))
                .andExpect(jsonPath("$.title").value("Aluguel"))
                .andExpect(jsonPath("$.totalAmount").value(2500.00))
                .andExpect(jsonPath("$.hasAttachment").value(true));

        ArgumentCaptor<CreateExpenseRequest> captor = ArgumentCaptor.forClass(CreateExpenseRequest.class);
        verify(expenseService).createExpense(eq("u1"), eq("house-1"), captor.capture());
        assertEquals("Aluguel", captor.getValue().getTitle());
        assertEquals(new BigDecimal("2500.00"), captor.getValue().getTotalAmount());
        assertEquals(LocalDate.of(2026, 11, 5), captor.getValue().getDueDate());
        assertEquals("YWJj", captor.getValue().getAttachmentBase64());
    }

    @Test
    void getAttachmentShouldReturnBytesForMemberAnd403ForNonMember() throws Exception {
        byte[] pdfBytes = "FAKE_PDF".getBytes(StandardCharsets.UTF_8);
        when(expenseService.getAttachment("u1", "house-1", "exp-1")).thenReturn(pdfBytes);
        when(expenseService.getAttachment("intruder", "house-1", "exp-1"))
                .thenThrow(new ForbiddenOperationException("User does not belong to this house"));

        mockMvc.perform(get("/api/houses/house-1/expenses/exp-1/attachment")
                        .principal(auth("u1")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(pdfBytes));

        mockMvc.perform(get("/api/houses/house-1/expenses/exp-1/attachment")
                        .principal(auth("intruder")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("User does not belong to this house"));
    }

    @Test
    void proposeAndVoteAndPayEndpointsShouldWork() throws Exception {
        ExpenseSplitDto splitDto = new ExpenseSplitDto();
        splitDto.setId("s1");
        splitDto.setUserId("u1");
        splitDto.setAmountDue(new BigDecimal("150.00"));
        splitDto.setIsPaid(true);

        ExpenseResponse response = new ExpenseResponse();
        response.setId("exp-1");
        response.setHouseId("house-1");
        response.setSplitType(SplitType.CUSTOM_APPROVED);
        response.setSplits(List.of(splitDto));

        when(expenseService.proposeCustomSplit(eq("u1"), eq("house-1"), eq("exp-1"), any(ProposeSplitRequest.class)))
                .thenReturn(response);
        when(expenseService.voteOnCustomSplit(eq("u2"), eq("house-1"), eq("exp-1"), any(VoteRequestDto.class)))
                .thenReturn(response);
        when(expenseService.markSplitAsPaid("u1", "house-1", "exp-1"))
                .thenReturn(response);

        mockMvc.perform(post("/api/houses/house-1/expenses/exp-1/propose-split")
                        .principal(auth("u1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDivisao\":\"VALOR\",\"divisoes\":[{\"usuarioId\":\"u1\",\"valorOuPorcentagem\":150.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("exp-1"));

        mockMvc.perform(post("/api/houses/house-1/expenses/exp-1/vote-split")
                        .principal(auth("u2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voto\":\"APROVAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType").value("CUSTOM_APPROVED"));

        mockMvc.perform(put("/api/houses/house-1/expenses/exp-1/pay")
                        .principal(auth("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits[0].isPaid").value(true));
    }

    @Test
    void createExpenseShouldBindPayerAndRecurrenceFields() throws Exception {
        ExpenseResponse response = new ExpenseResponse();
        response.setId("exp-2");
        response.setPaidBy(new UserRefDto("u2", "Bruno"));
        response.setCreatedById("u1");
        response.setRecurringDay(10);
        response.setRecurrenceActive(true);
        when(expenseService.createExpense(eq("u1"), eq("house-1"), any(CreateExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/houses/house-1/expenses")
                        .principal(auth("u1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Internet\",\"totalAmount\":100,\"dueDate\":\"2026-11-30\","
                                + "\"paidById\":\"u2\",\"recurringDay\":10,\"recurrenceActive\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidBy.id").value("u2"))
                .andExpect(jsonPath("$.paidBy.name").value("Bruno"))
                .andExpect(jsonPath("$.createdById").value("u1"))
                .andExpect(jsonPath("$.recurringDay").value(10))
                .andExpect(jsonPath("$.recurrenceActive").value(true));

        ArgumentCaptor<CreateExpenseRequest> captor = ArgumentCaptor.forClass(CreateExpenseRequest.class);
        verify(expenseService).createExpense(eq("u1"), eq("house-1"), captor.capture());
        assertEquals("u2", captor.getValue().getPaidById());
        assertEquals(10, captor.getValue().getRecurringDay());
        assertEquals(Boolean.TRUE, captor.getValue().getRecurrenceActive());
    }

    @Test
    void proposeSplitShouldReturn400WithUnifiedSumMessage() throws Exception {
        when(expenseService.proposeCustomSplit(eq("u1"), eq("house-1"), eq("exp-1"), any(ProposeSplitRequest.class)))
                .thenThrow(new BusinessRuleException("split must add up to the total"));

        mockMvc.perform(post("/api/houses/house-1/expenses/exp-1/propose-split")
                        .principal(auth("u1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoDivisao\":\"VALOR\",\"divisoes\":[{\"usuarioId\":\"u1\",\"valorOuPorcentagem\":10}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("split must add up to the total"));
    }

    @Test
    void confirmAndNotReceivedEndpointsShouldReturn200AndMapForbidden() throws Exception {
        ExpenseSplitDto splitDto = new ExpenseSplitDto();
        splitDto.setId("s2");
        splitDto.setUserId("u2");
        splitDto.setUserName("Bruno");
        splitDto.setIsPaid(false);
        ExpenseResponse response = new ExpenseResponse();
        response.setId("exp-1");
        response.setSplits(List.of(splitDto));

        when(expenseService.confirmReceived("u1", "house-1", "exp-1", "s2")).thenReturn(response);
        when(expenseService.markNotReceived("u1", "house-1", "exp-1", "s2")).thenReturn(response);
        when(expenseService.confirmReceived("u2", "house-1", "exp-1", "s2"))
                .thenThrow(new ForbiddenOperationException("only the payer can confirm payment"));

        mockMvc.perform(put("/api/houses/house-1/expenses/exp-1/splits/s2/confirm-received")
                        .principal(auth("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits[0].userName").value("Bruno"));

        mockMvc.perform(put("/api/houses/house-1/expenses/exp-1/splits/s2/not-received")
                        .principal(auth("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splits[0].isPaid").value(false));

        mockMvc.perform(put("/api/houses/house-1/expenses/exp-1/splits/s2/confirm-received")
                        .principal(auth("u2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the payer can confirm payment"));
    }

    @Test
    void updateAndDeleteEndpointsShouldReturn200And204AndMapConflict() throws Exception {
        ExpenseResponse response = new ExpenseResponse();
        response.setId("exp-1");
        response.setTitle("Novo titulo");
        when(expenseService.updateExpense(eq("u1"), eq("house-1"), eq("exp-1"), any(UpdateExpenseRequest.class)))
                .thenReturn(response);
        doThrow(new ConflictException("expense already has payments"))
                .when(expenseService).deleteExpense("u1", "house-1", "exp-busy");

        mockMvc.perform(put("/api/houses/house-1/expenses/exp-1")
                        .principal(auth("u1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Novo titulo\",\"totalAmount\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Novo titulo"));

        ArgumentCaptor<UpdateExpenseRequest> captor = ArgumentCaptor.forClass(UpdateExpenseRequest.class);
        verify(expenseService).updateExpense(eq("u1"), eq("house-1"), eq("exp-1"), captor.capture());
        assertEquals("Novo titulo", captor.getValue().getTitle());
        assertEquals(0, new BigDecimal("80").compareTo(captor.getValue().getTotalAmount()));

        mockMvc.perform(delete("/api/houses/house-1/expenses/exp-1")
                        .principal(auth("u1")))
                .andExpect(status().isNoContent());
        verify(expenseService).deleteExpense("u1", "house-1", "exp-1");

        mockMvc.perform(delete("/api/houses/house-1/expenses/exp-busy")
                        .principal(auth("u1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("expense already has payments"));
    }

    @Test
    void summaryEndpointShouldReturnDebtsCreditsAndMembers() throws Exception {
        ExpenseSummaryResponse summary = new ExpenseSummaryResponse(
                List.of(new ExpenseSummaryResponse.Debt("u2", "Bruno", new BigDecimal("30.00"))),
                List.of(new ExpenseSummaryResponse.Credit("u3", "Carla", new BigDecimal("100.00"))),
                List.of(new ExpenseSummaryResponse.MemberBalance("u1", "Ana", 1, 2, new BigDecimal("30.00"))));
        when(expenseService.getSummary("u1", "house-1")).thenReturn(summary);

        mockMvc.perform(get("/api/houses/house-1/expenses/summary")
                        .principal(auth("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myDebts[0].creditorId").value("u2"))
                .andExpect(jsonPath("$.myDebts[0].creditorName").value("Bruno"))
                .andExpect(jsonPath("$.myDebts[0].amount").value(30.00))
                .andExpect(jsonPath("$.owedToMe[0].debtorId").value("u3"))
                .andExpect(jsonPath("$.owedToMe[0].debtorName").value("Carla"))
                .andExpect(jsonPath("$.members[0].userId").value("u1"))
                .andExpect(jsonPath("$.members[0].paidCount").value(1))
                .andExpect(jsonPath("$.members[0].totalCount").value(2))
                .andExpect(jsonPath("$.members[0].openAmount").value(30.00));
    }
}
