package com.applypilot.token.dto;

/**
 * The answer to creating a token: the only time the secret is ever returned.
 *
 * @param token the full secret, to paste into the MCP client's configuration
 */
public record CreatedTokenResponse(TokenResponse details, String token) {

	@Override
	public String toString() {
		return "CreatedTokenResponse[details=" + details + ", token=***]";
	}

}
