package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial update: a null field keeps the current value. Pausing recurrence is done with
 * {@code recurrenceActive = false}.
 */
public class UpdateExpenseRequest {

    @JsonAlias("titulo")
    private String title;

    @JsonAlias("valorTotal")
    private BigDecimal totalAmount;

    @JsonAlias("vencimento")
    private LocalDate dueDate;

    @JsonAlias("pagoPorId")
    private String paidById;

    @JsonAlias("diaRecorrencia")
    private Integer recurringDay;

    @JsonAlias("recorrenciaAtiva")
    private Boolean recurrenceActive;

    public UpdateExpenseRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getPaidById() { return paidById; }
    public void setPaidById(String paidById) { this.paidById = paidById; }

    public Integer getRecurringDay() { return recurringDay; }
    public void setRecurringDay(Integer recurringDay) { this.recurringDay = recurringDay; }

    public Boolean getRecurrenceActive() { return recurrenceActive; }
    public void setRecurrenceActive(Boolean recurrenceActive) { this.recurrenceActive = recurrenceActive; }
}
