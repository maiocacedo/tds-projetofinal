package com.nossoteto.dto;

/**
 * A resident who has already voted on a request, without the vote direction (reused by B7).
 */
public record VoterSummaryDto(String id, String name) {}
