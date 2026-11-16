package com.applypilot.cv;

import com.applypilot.cv.CvService.CvFile;
import com.applypilot.cv.dto.CvResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The authenticated user's CV. There is no id in the path: a user only ever has, and can
 * only ever reach, their own CV.
 */
@RestController
@RequestMapping("/api/users/me/cv")
public class CvController {

	private final CvService cvService;

	public CvController(CvService cvService) {
		this.cvService = cvService;
	}

	/** Uploads the CV, replacing the previous one if there was one. */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public CvResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file)
			throws IOException {
		return cvService.upload(userId(jwt), file.getOriginalFilename(), file.getContentType(), file.getBytes());
	}

	@GetMapping
	public CvResponse metadata(@AuthenticationPrincipal Jwt jwt) {
		return cvService.getMetadata(userId(jwt));
	}

	@GetMapping("/file")
	public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt) {
		CvFile file = cvService.download(userId(jwt));
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(file.contentType()))
			// "attachment" makes the browser save the file instead of rendering it in our origin.
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment()
						.filename(file.fileName(), StandardCharsets.UTF_8)
						.build()
						.toString())
			.body(file.content());
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt) {
		cvService.delete(userId(jwt));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
