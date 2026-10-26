package com.nossoteto.service;

import com.nossoteto.model.House;
import com.nossoteto.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityCalculatorTest {

    private CompatibilityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CompatibilityCalculator();
    }

    @Test
    void calculateScoreShouldReturnNullWhenProfileOrHouseIsNull() {
        assertNull(calculator.calculateScore(null, new House()));
        assertNull(calculator.calculateScore(new UserProfile(), null));
        assertNull(calculator.calculateScore(null, null));
    }

    @Test
    void calculateScoreShouldReturn100WhenAllCriteriaMatchPerfectly() {
        UserProfile profile = new UserProfile();
        profile.setBudget(new BigDecimal("1500.00"));
        profile.setHasPets(true);
        profile.setIsSmoker(false);
        profile.setOrganizationLevel(4);
        profile.setVisitorTolerance(3);

        House house = new House();
        house.setRentAmount(new BigDecimal("1200.00"));
        house.setAllowsPets(true);
        house.setAllowsSmokers(false);
        house.setExpectedOrganizationLevel(4);
        house.setVisitorTolerance(3);

        assertEquals(100, calculator.calculateScore(profile, house));
    }

    @Test
    void calculateScoreShouldDeductBudgetPointsWhenRentExceedsBudget() {
        UserProfile profile = new UserProfile();
        profile.setBudget(new BigDecimal("1000.00"));
        profile.setHasPets(false);
        profile.setIsSmoker(false);
        profile.setOrganizationLevel(3);
        profile.setVisitorTolerance(3);

        House house = new House();
        house.setRentAmount(new BigDecimal("1200.00"));
        house.setAllowsPets(true);
        house.setAllowsSmokers(false);
        house.setExpectedOrganizationLevel(3);
        house.setVisitorTolerance(3);

        assertEquals(80, calculator.calculateScore(profile, house));
    }

    @Test
    void calculateScoreShouldDeductPetsAndSmokerPointsWhenIncompatible() {
        UserProfile profile = new UserProfile();
        profile.setBudget(new BigDecimal("2000.00"));
        profile.setHasPets(true);
        profile.setIsSmoker(true);
        profile.setOrganizationLevel(3);
        profile.setVisitorTolerance(3);

        House house = new House();
        house.setRentAmount(new BigDecimal("1500.00"));
        house.setAllowsPets(false);
        house.setAllowsSmokers(false);
        house.setExpectedOrganizationLevel(3);
        house.setVisitorTolerance(3);

        assertEquals(60, calculator.calculateScore(profile, house));
    }

    @Test
    void calculateScoreShouldDeduct5PointsPerDifferenceInOrganizationAndVisitors() {
        UserProfile profile = new UserProfile();
        profile.setBudget(new BigDecimal("1500.00"));
        profile.setHasPets(false);
        profile.setIsSmoker(false);
        profile.setOrganizationLevel(5); // diff 2 -> 20 - 10 = 10 pts
        profile.setVisitorTolerance(1);  // diff 4 -> 20 - 20 = 0 pts

        House house = new House();
        house.setRentAmount(new BigDecimal("1500.00"));
        house.setAllowsPets(false);
        house.setAllowsSmokers(false);
        house.setExpectedOrganizationLevel(3);
        house.setVisitorTolerance(5);

        assertEquals(70, calculator.calculateScore(profile, house));
    }

    @Test
    void calculateScoreShouldGiveFullPointsForNullOptionalAttributes() {
        UserProfile profile = new UserProfile();
        profile.setBudget(null);
        profile.setHasPets(null);
        profile.setIsSmoker(null);
        profile.setOrganizationLevel(null);
        profile.setVisitorTolerance(null);

        House house = new House();
        house.setRentAmount(null);
        house.setAllowsPets(false);
        house.setAllowsSmokers(false);
        house.setExpectedOrganizationLevel(5);
        house.setVisitorTolerance(5);

        assertEquals(100, calculator.calculateScore(profile, house));
    }
}
