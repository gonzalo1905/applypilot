package com.applypilot.demo;

import com.applypilot.analysis.AnalysisService;
import com.applypilot.analysis.dto.AnalyzeRequest;
import com.applypilot.application.ApplicationService;
import com.applypilot.application.dto.ApplicationResponse;
import com.applypilot.application.dto.CreateApplicationRequest;
import com.applypilot.auth.DemoAccount;
import com.applypilot.cv.Cv;
import com.applypilot.cv.CvRepository;
import com.applypilot.cv.CvService;
import com.applypilot.cv.CvStorage;
import com.applypilot.user.User;
import com.applypilot.user.UserRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the demo account with fictitious data when it does not exist (and, if asked,
 * recreates it), so a visitor of the public demo can try the whole flow without registering.
 *
 * <p>The postings of the demo cards are analyzed once, at creation, through the normal
 * analysis service (with the model when one is configured, keywords otherwise). The account
 * is kept between restarts on purpose: re-seeding on every wake-up of a free server would
 * spend AI calls each time.
 *
 * <p>The CV text lives in the database, but the PDF lives on the server's disk, which free
 * hosting wipes on restart. At every start the PDF is written again if it is missing.
 */
@Component
@ConditionalOnProperty(name = "app.access.demo-enabled", havingValue = "true")
public class DemoDataSeeder {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final UserRepository users;

	private final ApplicationService applications;

	private final AnalysisService analyses;

	private final CvService cvService;

	private final CvRepository cvs;

	private final CvStorage storage;

	private final PasswordEncoder passwordEncoder;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final boolean resetOnStart;

	public DemoDataSeeder(UserRepository users, ApplicationService applications, AnalysisService analyses,
			CvService cvService, CvRepository cvs, CvStorage storage, PasswordEncoder passwordEncoder,
			TransactionTemplate transaction, Clock clock,
			@Value("${app.access.demo-reset-on-start:false}") boolean resetOnStart) {
		this.users = users;
		this.applications = applications;
		this.analyses = analyses;
		this.cvService = cvService;
		this.cvs = cvs;
		this.storage = storage;
		this.passwordEncoder = passwordEncoder;
		this.transaction = transaction;
		this.clock = clock;
		this.resetOnStart = resetOnStart;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void seed() {
		try {
			Optional<User> existing = users.findByEmail(DemoAccount.EMAIL);
			if (existing.isPresent() && resetOnStart) {
				delete(existing.get());
				existing = Optional.empty();
			}
			if (existing.isPresent()) {
				restoreCvFile(existing.get().getId());
				return;
			}
			create();
		}
		catch (RuntimeException ex) {
			// The demo is a convenience: the application must start even if seeding fails.
			// The message names what failed (a path, a limit); it holds no secrets.
			log.error("Could not prepare the demo account: {}: {}", ex.getClass().getName(), ex.getMessage());
		}
	}

	private void create() {
		// Nobody can log in with this password: the demo is entered through "Try the demo".
		User demo = transaction.execute(status -> users.saveAndFlush(
				new User(DemoAccount.EMAIL, passwordEncoder.encode(UUID.randomUUID().toString()),
						DemoAccount.FULL_NAME)));
		UUID userId = demo.getId();
		try {
			cvService.upload(userId, DemoData.CV_FILE_NAME, "application/pdf", cvPdf());
			LocalDate today = LocalDate.now(clock);
			for (DemoData.Card card : DemoData.cards(today)) {
				ApplicationResponse created = applications.create(userId,
						new CreateApplicationRequest(card.company(), card.roleTitle(), null, card.location(),
								card.workMode(), null, card.notes(), card.status(), card.appliedAt()));
				if (card.posting() != null) {
					analyses.analyze(userId, created.id(), new AnalyzeRequest(card.posting()));
				}
			}
		}
		catch (RuntimeException ex) {
			// A half-built demo would stay half-built: an existing account is never re-seeded.
			// Removing it lets the next start try again from scratch.
			delete(demo);
			throw ex;
		}
		log.info("Demo account created");
	}

	private void delete(User demo) {
		try {
			cvService.delete(demo.getId());
		}
		catch (RuntimeException ex) {
			// No CV, or its file is already gone: nothing to clean up.
		}
		transaction.executeWithoutResult(status -> users.deleteById(demo.getId()));
		log.info("Demo account reset");
	}

	private void restoreCvFile(UUID userId) {
		Optional<Cv> cv = cvs.findByUserId(userId);
		if (cv.isPresent() && storage.load(cv.get().getStorageKey()).isEmpty()) {
			storage.store(cv.get().getStorageKey(), cvPdf());
		}
	}

	/** The demo CV as a one-page PDF, built in memory. */
	static byte[] cvPdf() {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
				content.setLeading(14);
				content.newLineAtOffset(56, 740);
				for (String line : DemoData.CV_LINES) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not build the demo CV", ex);
		}
	}

}
