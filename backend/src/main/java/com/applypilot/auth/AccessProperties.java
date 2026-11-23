package com.applypilot.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who may get an account, bound from {@code app.access.*}.
 *
 * @param registrationEnabled whether anyone can register. Off on the public demo, so strangers
 * cannot spend the shared AI quota with accounts of their own
 * @param demoEnabled whether the demo account exists and "Try the demo" can log into it
 */
@ConfigurationProperties(prefix = "app.access")
public record AccessProperties(boolean registrationEnabled, boolean demoEnabled) {
}
