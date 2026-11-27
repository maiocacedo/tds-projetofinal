package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class UpdateRecurrenceRequest {

    @JsonAlias({"ativa", "recorrenciaAtiva", "recurrenceActive"})
    private Boolean active;

    @JsonAlias({"dia", "diaRecorrencia", "recurringDay"})
    private Integer day;

    public UpdateRecurrenceRequest() {}

    public UpdateRecurrenceRequest(Boolean active, Integer day) {
        this.active = active;
        this.day = day;
    }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Integer getDay() { return day; }
    public void setDay(Integer day) { this.day = day; }
}
