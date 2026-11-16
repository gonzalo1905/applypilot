package com.applypilot.application;

import com.applypilot.application.dto.ApplicationDetails;
import com.applypilot.application.dto.ApplicationResponse;
import com.applypilot.application.dto.CreateApplicationRequest;
import com.applypilot.application.dto.MoveApplicationRequest;
import com.applypilot.application.dto.UpdateApplicationRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for the Kanban board. Every method takes the caller's id and only ever
 * touches that user's cards.
 *
 * <p>Ordering: each card has a zero-based {@code position} inside its column. After any
 * change to a column (move, delete) the column is renumbered 0..n-1. Two simultaneous
 * requests could leave two cards with the same position; reads break that tie by creation
 * time and the next move renumbers the column, so the board heals itself.
 */
@Service
public class ApplicationService {

	private final JobApplicationRepository repository;

	private final Clock clock;

	public ApplicationService(JobApplicationRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ApplicationResponse> list(UUID userId) {
		return repository.findByUserIdOrderByPositionAscCreatedAtAsc(userId)
			.stream()
			.map(ApplicationResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public ApplicationResponse get(UUID userId, UUID id) {
		return ApplicationResponse.from(findOwned(userId, id));
	}

	@Transactional
	public ApplicationResponse create(UUID userId, CreateApplicationRequest request) {
		ApplicationStatus status = request.status() != null ? request.status() : ApplicationStatus.SAVED;
		// New cards go to the end of their column.
		int position = repository.countByUserIdAndStatus(userId, status);
		JobApplication application = new JobApplication(userId, status, position);
		applyDetails(application, request);
		stampAppliedDateIfNeeded(application);
		return ApplicationResponse.from(repository.save(application));
	}

	@Transactional
	public ApplicationResponse update(UUID userId, UUID id, UpdateApplicationRequest request) {
		JobApplication application = findOwned(userId, id);
		LocalDate previousAppliedAt = application.getAppliedAt();
		applyDetails(application, request);
		// A card in APPLIED always has a date: an edit that sends none keeps the one it had.
		if (application.getStatus() == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
			application.setAppliedAt(previousAppliedAt != null ? previousAppliedAt : LocalDate.now(clock));
		}
		application.touch(clock.instant());
		return ApplicationResponse.from(application);
	}

	@Transactional
	public ApplicationResponse move(UUID userId, UUID id, MoveApplicationRequest request) {
		JobApplication application = findOwned(userId, id);
		ApplicationStatus sourceStatus = application.getStatus();
		ApplicationStatus targetStatus = request.status();

		List<JobApplication> target = columnWithout(userId, targetStatus, id);
		// Clamp to the column: past the end means "last". The request is validated, but the
		// service must not depend on that to stay inside the list.
		int index = Math.max(0, Math.min(request.position(), target.size()));
		target.add(index, application);

		application.setStatus(targetStatus);
		renumber(target);
		if (sourceStatus != targetStatus) {
			// Close the gap the card left behind in its old column.
			renumber(columnWithout(userId, sourceStatus, id));
			stampAppliedDateIfNeeded(application);
		}
		application.touch(clock.instant());
		return ApplicationResponse.from(application);
	}

	@Transactional
	public void delete(UUID userId, UUID id) {
		JobApplication application = findOwned(userId, id);
		repository.delete(application);
		renumber(columnWithout(userId, application.getStatus(), id));
	}

	private JobApplication findOwned(UUID userId, UUID id) {
		return repository.findByIdAndUserId(id, userId).orElseThrow(ApplicationNotFoundException::new);
	}

	/** The user's cards in one column, in order, excluding the card being moved or deleted. */
	private List<JobApplication> columnWithout(UUID userId, ApplicationStatus status, UUID excludedId) {
		List<JobApplication> column = new ArrayList<>(
				repository.findByUserIdAndStatusOrderByPositionAscCreatedAtAsc(userId, status));
		column.removeIf(card -> card.getId().equals(excludedId));
		return column;
	}

	/** Loaded entities are managed, so changing a position is saved when the transaction commits. */
	private static void renumber(List<JobApplication> column) {
		for (int i = 0; i < column.size(); i++) {
			column.get(i).setPosition(i);
		}
	}

	/** A card that reaches APPLIED without a date gets today's date; an existing date is kept. */
	private void stampAppliedDateIfNeeded(JobApplication application) {
		if (application.getStatus() == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
			application.setAppliedAt(LocalDate.now(clock));
		}
	}

	private static void applyDetails(JobApplication application, ApplicationDetails details) {
		application.setCompany(details.company().trim());
		application.setRoleTitle(details.roleTitle().trim());
		application.setJobUrl(trimToNull(details.jobUrl()));
		application.setLocation(trimToNull(details.location()));
		application.setWorkMode(details.workMode());
		application.setSalaryNote(trimToNull(details.salaryNote()));
		application.setNotes(trimToNull(details.notes()));
		application.setAppliedAt(details.appliedAt());
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
