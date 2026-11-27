package com.nossoteto.dto;

/**
 * Candidate card on the "Receber pedidos" screen. {@code compatibilityScore} is null while the profile is incomplete.
 */
public record CandidateCardDto(String id, String name, Integer compatibilityScore, int points, Integer residenceMonths) {}
