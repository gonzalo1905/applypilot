package com.applypilot.analysis;

import com.applypilot.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The user has used all their AI calls for today. Answered with 429 (Too Many Requests)
 * rather than silently switching to the keyword extractor, so the user knows what happened.
 */
public class AiDailyLimitReachedException extends ApiException {

	public AiDailyLimitReachedException(int dailyLimit) {
		super(HttpStatus.TOO_MANY_REQUESTS, "AI_DAILY_LIMIT_REACHED", "You have used your " + dailyLimit
				+ " AI calls for today. Try again tomorrow.");
	}

}
