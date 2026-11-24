package com.applypilot.insights.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Figures for the board header and cards.
 *
 * @param matchScores the latest match score (0 to 100) of each application that has one;
 * applications never matched are absent
 * @param aiCallsLeft AI calls the user still has today
 * @param aiDailyLimit AI calls each user gets per day
 */
public record InsightsResponse(Map<UUID, Integer> matchScores, int aiCallsLeft, int aiDailyLimit) {
}
