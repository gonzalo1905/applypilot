package com.applypilot.agent;

import com.applypilot.agent.ReviewerAnswer.FabricatedClaim;
import java.util.List;

/**
 * The feedback on one draft, combining the Reviewer model's judgment with checks done in
 * code.
 *
 * @param approved true when the letter can go to the user without another revision: the
 * model approved it (or was unavailable), it has no unsupported claims and its length is fine
 * @param lengthIssue set by code when the letter is too short or too long; null otherwise
 * @param wordCount counted by code
 * @param aiChecked false when the model was unavailable and only the code checks ran, so
 * claims were not compared with the CV
 */
public record Review(boolean approved, List<FabricatedClaim> fabricatedClaims, List<String> missingRequirements,
		List<String> toneIssues, String lengthIssue, List<String> suggestions, int wordCount, boolean aiChecked) {
}
