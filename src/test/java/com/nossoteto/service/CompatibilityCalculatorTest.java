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

    private UserProfile completeProfile() {
        UserProfile profile = new UserProfile();
        profile.setBudget(new BigDecimal("1500.00"));
        profile.setHasPets(false);
        profile.setIsSmoker(false);
        profile.setOrganizationLevel(3);
        profile.setVisitorTolerance(3);
        return profile;
    }

    @Test
    void isProfileCompleteShouldReturnTrueOnlyWhenAllFiveMatchFieldsArePresent() {
        assertFalse(CompatibilityCalculator.isProfileComplete(null));
        assertTrue(CompatibilityCalculator.isProfileComplete(completeProfile()));

        UserProfile missingBudget = completeProfile();
        missingBudget.setBudget(null);
        assertFalse(CompatibilityCalculator.isProfileComplete(missingBudget));

        UserProfile missingPets = completeProfile();
        missingPets.setHasPets(null);
        assertFalse(CompatibilityCalculator.isProfileComplete(missingPets));

        UserProfile missingSmoker = completeProfile();
        missingSmoker.setIsSmoker(null);
        assertFalse(CompatibilityCalculator.isProfileComplete(missingSmoker));

        UserProfile missingOrg = completeProfile();
        missingOrg.setOrganizationLevel(null);
        assertFalse(CompatibilityCalculator.isProfileComplete(missingOrg));

        UserProfile missingVisitors = completeProfile();
        missingVisitors.setVisitorTolerance(null);
        assertFalse(CompatibilityCalculator.isProfileComplete(missingVisitors));
    }

    @Test
    void calculateScoreShouldReturnNullWhenAnyRequiredProfileFieldIsMissing() {
        House house = new House();
        house.setRentAmount(new BigDecimal("1200.00"));
        house.setAllowsPets(true);
        house.setAllowsSmokers(true);
        house.setExpectedOrganizationLevel(3);
        house.setVisitorTolerance(3);

        UserProfile missingBudget = completeProfile();
        missingBudget.setBudget(null);
        assertNull(calculator.calculateScore(missingBudget, house));

        UserProfile missingPets = completeProfile();
        missingPets.setHasPets(null);
        assertNull(calculator.calculateScore(missingPets, house));

        UserProfile missingSmoker = completeProfile();
        missingSmoker.setIsSmoker(null);
        assertNull(calculator.calculateScore(missingSmoker, house));

        UserProfile missingOrg = completeProfile();
        missingOrg.setOrganizationLevel(null);
        assertNull(calculator.calculateScore(missingOrg, house));

        UserProfile missingVisitors = completeProfile();
        missingVisitors.setVisitorTolerance(null);
        assertNull(calculator.calculateScore(missingVisitors, house));
    }

    @Test
    void calculateScoreShouldGiveFullPointsWhenHouseOptionalAttributesAreNullAndProfileIsComplete() {
        UserProfile profile = completeProfile();

        House house = new House();
        house.setRentAmount(null);
        house.setAllowsPets(false);
        house.setAllowsSmokers(false);
        house.setExpectedOrganizationLevel(null);
        house.setVisitorTolerance(null);

        assertEquals(100, calculator.calculateScore(profile, house));
    }
}
