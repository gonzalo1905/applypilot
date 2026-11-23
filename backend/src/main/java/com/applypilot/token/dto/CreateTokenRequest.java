package com.applypilot.token.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param name a label to recognize the token later, such as "Claude Desktop on my laptop" */
public record CreateTokenRequest(@NotBlank @Size(max = 60) String name) {
}
