package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record UpdatePermissionsRequest(@JsonAlias("permissoes") List<String> permissions) {}
