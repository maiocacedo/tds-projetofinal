package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PasswordResetConfirmDto(
        String token,
        @JsonAlias("novaSenha") String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetConfirmDto[token=******, newPassword=******]";
    }
}
