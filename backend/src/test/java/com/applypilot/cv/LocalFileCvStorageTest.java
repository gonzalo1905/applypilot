package com.applypilot.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class LocalFileCvStorageTest {

	@TempDir
	Path tempDir;

	private Path root;

	private LocalFileCvStorage storage;

	@BeforeEach
	void setUp() {
		root = tempDir.resolve("storage");
		storage = new LocalFileCvStorage(new CvProperties(DataSize.ofMegabytes(5), root.toString()));
	}

	@Test
	void storesLoadsAndDeletesAFile() {
		byte[] content = "pdf-bytes".getBytes(StandardCharsets.UTF_8);

		storage.store("cvs/one.pdf", content);

		assertThat(root.resolve("cvs/one.pdf")).exists();
		assertThat(storage.load("cvs/one.pdf")).contains(content);

		storage.delete("cvs/one.pdf");

		assertThat(storage.load("cvs/one.pdf")).isEmpty();
	}

	@Test
	void loadingOrDeletingAMissingKeyIsNotAnError() {
		assertThat(storage.load("cvs/missing.pdf")).isEmpty();
		storage.delete("cvs/missing.pdf");
	}

	@Test
	void refusesKeysThatWouldEscapeTheStorageFolder() throws Exception {
		Path outside = tempDir.resolve("outside.txt");
		Files.writeString(outside, "secret");

		assertThatThrownBy(() -> storage.load("../outside.txt")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.store("../evil.pdf", new byte[] { 1 }))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.delete("../outside.txt")).isInstanceOf(IllegalArgumentException.class);

		assertThat(outside).exists();
		assertThat(tempDir.resolve("evil.pdf")).doesNotExist();
	}

}
