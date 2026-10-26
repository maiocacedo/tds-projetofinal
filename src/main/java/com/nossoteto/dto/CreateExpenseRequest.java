package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateExpenseRequest {

    @JsonAlias("titulo")
    private String title;

    @JsonAlias("valorTotal")
    private BigDecimal totalAmount;

    @JsonAlias("vencimento")
    private LocalDate dueDate;

    @JsonAlias("anexoBase64")
    private String attachmentBase64;

    public CreateExpenseRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getAttachmentBase64() { return attachmentBase64; }
    public void setAttachmentBase64(String attachmentBase64) { this.attachmentBase64 = attachmentBase64; }
}
