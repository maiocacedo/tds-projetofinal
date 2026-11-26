package com.nossoteto.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseSummaryResponse(
        List<Debt> myDebts,
        List<Credit> owedToMe,
        List<MemberBalance> members) {

    public record Debt(String creditorId, String creditorName, BigDecimal amount) {}

    public record Credit(String debtorId, String debtorName, BigDecimal amount) {}

    public record MemberBalance(String userId, String name, int paidCount, int totalCount, BigDecimal openAmount) {}
}
