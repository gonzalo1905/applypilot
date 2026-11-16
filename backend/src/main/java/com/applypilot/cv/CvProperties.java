package com.applypilot.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * CV upload settings bound from {@code app.cv.*}.
 *
 * @param maxSize largest accepted file
 * @param storageDir folder where {@link LocalFileCvStorage} keeps the files
 */
@Validated
@ConfigurationProperties(prefix = "app.cv")
public record CvProperties(@NotNull DataSize maxSize, @NotBlank String storageDir) {
}
