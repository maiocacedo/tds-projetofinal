package com.nossoteto.controller;

import com.nossoteto.dto.CreateExpenseRequest;
import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.ProposeSplitRequest;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.model.enums.SplitType;
import com.nossoteto.security.JwtAuthenticationFilter;
import com.nossoteto.security.JwtTokenProvider;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

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
}
