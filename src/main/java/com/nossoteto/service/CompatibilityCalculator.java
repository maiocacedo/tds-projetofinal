package com.nossoteto.service;

import com.nossoteto.model.House;
import com.nossoteto.model.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class CompatibilityCalculator {

    public Integer calculateScore(UserProfile profile, House house) {
        if (profile == null || house == null) {
            return null;
        }

        int score = 0;

        // 1. Budget (20 pts)
        if (house.getRentAmount() == null
                || profile.getBudget() == null
                || profile.getBudget().compareTo(house.getRentAmount()) >= 0) {
            score += 20;
        }

        // 2. Pets (20 pts)
        if (!(Boolean.TRUE.equals(profile.getHasPets()) && Boolean.FALSE.equals(house.getAllowsPets()))) {
            score += 20;
        }

        // 3. Smoker (20 pts)
        if (!(Boolean.TRUE.equals(profile.getIsSmoker()) && Boolean.FALSE.equals(house.getAllowsSmokers()))) {
            score += 20;
        }

        // 4. Organization Level (20 pts)
        if (profile.getOrganizationLevel() == null || house.getExpectedOrganizationLevel() == null) {
            score += 20;
        } else {
            int diff = Math.abs(profile.getOrganizationLevel() - house.getExpectedOrganizationLevel());
            score += Math.max(0, 20 - diff * 5);
        }

        // 5. Visitor Tolerance (20 pts)
        if (profile.getVisitorTolerance() == null || house.getVisitorTolerance() == null) {
            score += 20;
        } else {
            int diff = Math.abs(profile.getVisitorTolerance() - house.getVisitorTolerance());
            score += Math.max(0, 20 - diff * 5);
        }

        return score;
    }
}
