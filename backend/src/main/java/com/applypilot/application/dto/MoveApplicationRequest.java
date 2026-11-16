package com.applypilot.application.dto;

import com.applypilot.application.ApplicationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Drag-and-drop target: the destination column and the zero-based index inside it.
 * A position past the end of the column means "last".
 */
public record MoveApplicationRequest(@NotNull ApplicationStatus status, @NotNull @Min(0) Integer position) {
}
