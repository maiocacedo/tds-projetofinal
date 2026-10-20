package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.util.List;

public class ProposeSplitRequest {

    @JsonAlias("divisoes")
    private List<SplitItem> splits;

    @JsonAlias("tipoDivisao")
    private String splitMode;

    public ProposeSplitRequest() {}

    public List<SplitItem> getSplits() { return splits; }
    public void setSplits(List<SplitItem> splits) { this.splits = splits; }

    public String getSplitMode() { return splitMode; }
    public void setSplitMode(String splitMode) { this.splitMode = splitMode; }

    public static class SplitItem {

        @JsonAlias("usuarioId")
        private String userId;

        @JsonAlias({"valorOuPorcentagem", "amount", "percentage"})
        private BigDecimal amountOrPercentage;

        public SplitItem() {}

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public BigDecimal getAmountOrPercentage() { return amountOrPercentage; }
        public void setAmountOrPercentage(BigDecimal amountOrPercentage) { this.amountOrPercentage = amountOrPercentage; }
    }
}
