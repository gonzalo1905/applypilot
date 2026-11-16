package com.applypilot.cv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Keeps CV files in a folder on the local disk. Good for development and single-server
 * deployments; hosts with ephemeral disks need a cloud implementation of {@link CvStorage}.
 */
@Component
@EnableConfigurationProperties(CvProperties.class)
public class LocalFileCvStorage implements CvStorage {

	private final Path root;

	public LocalFileCvStorage(CvProperties properties) {
		this.root = Path.of(properties.storageDir()).toAbsolutePath().normalize();
	}

	@Override
	public void store(String key, byte[] content) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not store file " + key, ex);
		}
	}

	@Override
	public Optional<byte[]> load(String key) {
		Path source = resolve(key);
		if (!Files.isRegularFile(source)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readAllBytes(source));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read file " + key, ex);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not delete file " + key, ex);
		}
	}

	/**
	 * Maps a key to a path inside the storage folder. Keys come from the application, but a
	 * key such as {@code ../../etc/passwd} must never be able to escape the folder.
	 */
	private Path resolve(String key) {
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root) || path.equals(root)) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return path;
	}

}
