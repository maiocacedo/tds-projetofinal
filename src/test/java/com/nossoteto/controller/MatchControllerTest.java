package com.nossoteto.controller;

import com.nossoteto.dto.CreateMatchRequestDto;
import com.nossoteto.dto.MatchRequestResponse;
import com.nossoteto.dto.VoteMatchResponse;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.security.JwtAuthenticationFilter;
import com.nossoteto.security.JwtTokenProvider;
import com.nossoteto.service.MatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchController.class)
@AutoConfigureMockMvc(addFilters = false)
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchService matchService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UsernamePasswordAuthenticationToken auth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    }

    @Test
    void createMatchRequestShouldAcceptPortugueseAliasAndReturn201() throws Exception {
        MatchRequestResponse response = new MatchRequestResponse();
        response.setId("req-1");
        response.setUserId("candidate-1");
        response.setHouseId("house-1");
        response.setStatus(MatchStatus.PENDING);

        when(matchService.createMatchRequest(eq("candidate-1"), any(CreateMatchRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/matches/requests")
                        .principal(auth("candidate-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"casaId\":\"house-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("req-1"))
                .andExpect(jsonPath("$.houseId").value("house-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<CreateMatchRequestDto> captor = ArgumentCaptor.forClass(CreateMatchRequestDto.class);
        verify(matchService).createMatchRequest(eq("candidate-1"), captor.capture());
        assertEquals("house-1", captor.getValue().getHouseId());
    }

    @Test
    void listHouseMatchesShouldReturn403WhenUserIsNotMember() throws Exception {
        when(matchService.listHouseMatchRequests("outsider-1", "house-1"))
                .thenThrow(new ForbiddenOperationException("User does not belong to this house"));

        mockMvc.perform(get("/api/houses/house-1/matches")
                        .principal(auth("outsider-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("User does not belong to this house"));
    }

    @Test
    void voteOnMatchRequestShouldAcceptPortugueseAliasAndReturnOutcome() throws Exception {
        VoteMatchResponse voteResponse = new VoteMatchResponse();
        voteResponse.setRequestId("req-1");
        voteResponse.setStatus(MatchStatus.ACCEPTED);
        voteResponse.setApproveVotes(2);
        voteResponse.setRejectVotes(0);
        voteResponse.setTotalHouseMembers(2);
        voteResponse.setHasOtherPendingRequests(true);
        voteResponse.setOtherPendingRequestIds(List.of("req-2"));

        when(matchService.voteOnMatchRequest(eq("voter-1"), eq("req-1"), any(VoteRequestDto.class)))
                .thenReturn(voteResponse);

        mockMvc.perform(post("/api/matches/requests/req-1/vote")
                        .principal(auth("voter-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voto\":\"APROVAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.hasOtherPendingRequests").value(true))
                .andExpect(jsonPath("$.otherPendingRequestIds[0]").value("req-2"));

        ArgumentCaptor<VoteRequestDto> captor = ArgumentCaptor.forClass(VoteRequestDto.class);
        verify(matchService).voteOnMatchRequest(eq("voter-1"), eq("req-1"), captor.capture());
        assertEquals("APROVAR", captor.getValue().getVote());
    }

    @Test
    void cancelOtherPendingRequestsShouldReturnCancelledCount() throws Exception {
        when(matchService.cancelOtherPendingRequests("candidate-1"))
                .thenReturn(Map.of("cancelledCount", 2));

        mockMvc.perform(post("/api/matches/requests/cancel-others")
                        .principal(auth("candidate-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount").value(2));
    }
}
