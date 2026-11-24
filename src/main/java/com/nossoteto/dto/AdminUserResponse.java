package com.nossoteto.dto;

import java.util.List;

public record AdminUserResponse(String id, String name, String email, String houseId, List<String> permissions) {}
