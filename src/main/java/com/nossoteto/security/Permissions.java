package com.nossoteto.security;

import java.util.Set;

public final class Permissions {
    public static final String MANAGE_PROFILE = "ROLE_MANAGE_PROFILE";
    public static final String SEARCH_HOUSES = "ROLE_SEARCH_HOUSES";
    public static final String MANAGE_HOUSE = "ROLE_MANAGE_HOUSE";
    public static final String MATCH = "ROLE_MATCH";
    public static final String MANAGE_EXPENSES = "ROLE_MANAGE_EXPENSES";
    public static final String MANAGE_CHORES = "ROLE_MANAGE_CHORES";
    public static final String MANAGE_GROCERIES = "ROLE_MANAGE_GROCERIES";
    public static final String VIEW_DASHBOARD = "ROLE_VIEW_DASHBOARD";
    public static final String ADMIN_USERS = "ROLE_ADMIN_USERS";

    public static final Set<String> DEFAULT_USER = Set.of(
            MANAGE_PROFILE, SEARCH_HOUSES, MANAGE_HOUSE, MATCH,
            MANAGE_EXPENSES, MANAGE_CHORES, MANAGE_GROCERIES, VIEW_DASHBOARD);

    private Permissions() {}
}
