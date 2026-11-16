package com.applypilot.application;

import static com.applypilot.application.ApplicationStatus.APPLIED;
import static com.applypilot.application.ApplicationStatus.INTERVIEW;
import static com.applypilot.application.ApplicationStatus.OFFER;
import static com.applypilot.application.ApplicationStatus.REJECTED;
import static com.applypilot.application.ApplicationStatus.SAVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.applypilot.common.error.InvalidRequestException;
import com.applypilot.application.dto.ApplicationResponse;
import com.applypilot.application.dto.CreateApplicationRequest;
import com.applypilot.application.dto.MoveApplicationRequest;
import com.applypilot.application.dto.UpdateApplicationRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The mocked repository answers from an in-memory {@link #board}, the way the database would:
 * finders filter by owner (and column) and return cards in position order. Tests put cards on
 * the board, call the service and then look at where the cards ended up.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-03-10T09:30:00Z");

	private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

	private static final Instant EARLIER = Instant.parse("2026-01-15T08:00:00Z");

	private static final LocalDate EXPLICIT_DATE = LocalDate.of(2026, 2, 1);

	private static final UUID USER_ID = UUID.randomUUID();

	private static final UUID OTHER_USER_ID = UUID.randomUUID();

	@Mock
	private JobApplicationRepository repository;

	private ApplicationService applicationService;

	/** Every card "in the database", of every user, in creation order. */
	private final List<JobApplication> board = new ArrayList<>();

	@BeforeEach
	void setUp() {
		applicationService = new ApplicationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

		// Lenient: each test only uses some of these queries.
		lenient().when(repository.findByIdAndUserId(any(), any()))
			.thenAnswer(invocation -> board.stream()
				.filter(card -> card.getId().equals(invocation.getArgument(0))
						&& card.getUserId().equals(invocation.getArgument(1)))
				.findFirst());
		lenient().when(repository.findByUserIdOrderByPositionAscCreatedAtAsc(any()))
			.thenAnswer(invocation -> inPositionOrder(
					board.stream().filter(card -> card.getUserId().equals(invocation.getArgument(0))).toList()));
		lenient().when(repository.findByUserIdAndStatusOrderByPositionAscCreatedAtAsc(any(), any()))
			.thenAnswer(invocation -> cardsIn(invocation.getArgument(0), invocation.getArgument(1)));
		lenient().when(repository.countByUserIdAndStatus(any(), any()))
			.thenAnswer(invocation -> cardsIn(invocation.getArgument(0), invocation.getArgument(1)).size());
		lenient().when(repository.save(any(JobApplication.class))).thenAnswer(invocation -> {
			JobApplication card = invocation.getArgument(0);
			// The id is normally assigned by JPA when the entity is persisted.
			ReflectionTestUtils.setField(card, "id", UUID.randomUUID());
			board.add(card);
			return card;
		});
		lenient().doAnswer(invocation -> {
			board.remove(invocation.<JobApplication>getArgument(0));
			return null;
		}).when(repository).delete(any(JobApplication.class));
	}

	// list

	@Test
	void listReturnsOnlyTheCallersCardsInTheRepositoryOrder() {
		myCard("Acme", SAVED, 0);
		myCard("Globex", APPLIED, 0);
		myCard("Initech", SAVED, 1);
		otherUsersCard("Umbrella", SAVED, 0);

		List<ApplicationResponse> responses = applicationService.list(USER_ID);

		assertThat(responses).extracting(ApplicationResponse::company, ApplicationResponse::status,
				ApplicationResponse::position)
			.containsExactly(tuple("Acme", SAVED, 0), tuple("Globex", APPLIED, 0), tuple("Initech", SAVED, 1));
	}

	@Test
	void listIsEmptyWhenTheUserHasNoCards() {
		otherUsersCard("Umbrella", SAVED, 0);

		assertThat(applicationService.list(USER_ID)).isEmpty();
	}

	// get

	@Test
	void getReturnsEveryFieldOfTheOwnedCard() {
		JobApplication card = myCard("Acme", INTERVIEW, 2);
		card.setJobUrl("https://acme.example/jobs/1");
		card.setLocation("Madrid");
		card.setWorkMode(WorkMode.HYBRID);
		card.setSalaryNote("50-60k");
		card.setNotes("Referred by Ana");
		card.setAppliedAt(EXPLICIT_DATE);
		ReflectionTestUtils.setField(card, "createdAt", EARLIER);
		card.touch(NOW);

		ApplicationResponse response = applicationService.get(USER_ID, card.getId());

		assertThat(response).isEqualTo(new ApplicationResponse(card.getId(), "Acme", "Backend Developer",
				"https://acme.example/jobs/1", "Madrid", WorkMode.HYBRID, "50-60k", "Referred by Ana", INTERVIEW, 2,
				EXPLICIT_DATE, EARLIER, NOW));
	}

	@Test
	void getOfACardThatDoesNotExistThrowsNotFound() {
		myCard("Acme", SAVED, 0);

		assertThatThrownBy(() -> applicationService.get(USER_ID, UUID.randomUUID()))
			.isInstanceOf(ApplicationNotFoundException.class);
	}

	@Test
	void getOfAnotherUsersCardThrowsNotFound() {
		JobApplication theirs = otherUsersCard("Umbrella", SAVED, 0);

		assertThatThrownBy(() -> applicationService.get(USER_ID, theirs.getId()))
			.isInstanceOf(ApplicationNotFoundException.class);
	}

	// create

	@Test
	void createDefaultsToSavedAndAppendsAtTheEndOfTheColumn() {
		myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		myCard("Initech", APPLIED, 0);

		ApplicationResponse response = applicationService.create(USER_ID, createRequest(null, null));

		assertThat(response.status()).isEqualTo(SAVED);
		assertThat(response.position()).isEqualTo(2);
		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Acme", 0), tuple("Globex", 1), tuple("Hooli", 2));
	}

	@Test
	void createInAnEmptyColumnPutsTheCardFirst() {
		myCard("Acme", APPLIED, 0);
		otherUsersCard("Umbrella", SAVED, 0);

		ApplicationResponse response = applicationService.create(USER_ID, createRequest(null, null));

		assertThat(response.position()).isZero();
	}

	@Test
	void createWithAStatusAppendsAtTheEndOfThatColumn() {
		myCard("Acme", SAVED, 0);
		myCard("Globex", INTERVIEW, 0);
		myCard("Initech", INTERVIEW, 1);
		myCard("Massive", INTERVIEW, 2);

		ApplicationResponse response = applicationService.create(USER_ID, createRequest(INTERVIEW, null));

		assertThat(response.status()).isEqualTo(INTERVIEW);
		assertThat(response.position()).isEqualTo(3);
	}

	@Test
	void createSavesTheCardForTheCallerAndReturnsIt() {
		ApplicationResponse response = applicationService.create(USER_ID,
				new CreateApplicationRequest("Hooli", "Backend Developer", "https://hooli.example/jobs/7", "Madrid",
						WorkMode.REMOTE, "50-60k", "Referred by Ana", OFFER, EXPLICIT_DATE));

		ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);

		// The timestamps are set by JPA on persist, which a mocked repository does not do.
		assertThat(response).usingRecursiveComparison()
			.ignoringFields("createdAt", "updatedAt")
			.isEqualTo(new ApplicationResponse(saved.getValue().getId(), "Hooli", "Backend Developer",
					"https://hooli.example/jobs/7", "Madrid", WorkMode.REMOTE, "50-60k", "Referred by Ana", OFFER, 0,
					EXPLICIT_DATE, null, null));
	}

	@Test
	void createTrimsText() {
		ApplicationResponse response = applicationService.create(USER_ID,
				new CreateApplicationRequest("  Hooli ", " Backend Developer  ", " https://hooli.example/jobs/7 ",
						"  Madrid ", null, " 50-60k ", "  Referred by Ana\n", null, null));

		assertThat(response.company()).isEqualTo("Hooli");
		assertThat(response.roleTitle()).isEqualTo("Backend Developer");
		assertThat(response.jobUrl()).isEqualTo("https://hooli.example/jobs/7");
		assertThat(response.location()).isEqualTo("Madrid");
		assertThat(response.salaryNote()).isEqualTo("50-60k");
		assertThat(response.notes()).isEqualTo("Referred by Ana");
	}

	@Test
	void createTurnsBlankOptionalFieldsIntoNull() {
		ApplicationResponse response = applicationService.create(USER_ID,
				new CreateApplicationRequest("Hooli", "Backend Developer", "", "   ", null, " \t", "\n", null, null));

		assertThat(response.jobUrl()).isNull();
		assertThat(response.location()).isNull();
		assertThat(response.salaryNote()).isNull();
		assertThat(response.notes()).isNull();
	}

	@Test
	void createAsAppliedWithoutADateStampsTodaysDate() {
		ApplicationResponse response = applicationService.create(USER_ID, createRequest(APPLIED, null));

		assertThat(response.appliedAt()).isEqualTo(TODAY);
	}

	@Test
	void createAsAppliedKeepsAnExplicitDate() {
		ApplicationResponse response = applicationService.create(USER_ID, createRequest(APPLIED, EXPLICIT_DATE));

		assertThat(response.appliedAt()).isEqualTo(EXPLICIT_DATE);
	}

	@Test
	void createOutsideAppliedDoesNotStampADate() {
		assertThat(applicationService.create(USER_ID, createRequest(null, null)).appliedAt()).isNull();
		assertThat(applicationService.create(USER_ID, createRequest(INTERVIEW, null)).appliedAt()).isNull();
	}

	// update

	@Test
	void updateChangesTheDetailsButNeverStatusOrPosition() {
		JobApplication first = myCard("Acme", INTERVIEW, 0);
		JobApplication card = myCard("Globex", INTERVIEW, 1);
		JobApplication last = myCard("Initech", INTERVIEW, 2);

		ApplicationResponse response = applicationService.update(USER_ID, card.getId(),
				new UpdateApplicationRequest("Globex Corp", "Staff Engineer", "https://globex.example/jobs/9", "Lisbon",
						WorkMode.ONSITE, "70k", "Second round booked", EXPLICIT_DATE));

		assertThat(response.id()).isEqualTo(card.getId());
		assertThat(response.company()).isEqualTo("Globex Corp");
		assertThat(response.roleTitle()).isEqualTo("Staff Engineer");
		assertThat(response.jobUrl()).isEqualTo("https://globex.example/jobs/9");
		assertThat(response.location()).isEqualTo("Lisbon");
		assertThat(response.workMode()).isEqualTo(WorkMode.ONSITE);
		assertThat(response.salaryNote()).isEqualTo("70k");
		assertThat(response.notes()).isEqualTo("Second round booked");
		assertThat(response.appliedAt()).isEqualTo(EXPLICIT_DATE);
		assertThat(response.status()).isEqualTo(INTERVIEW);
		assertThat(response.position()).isEqualTo(1);

		assertThat(card.getCompany()).isEqualTo("Globex Corp");
		assertThat(card.getStatus()).isEqualTo(INTERVIEW);
		assertThat(card.getPosition()).isEqualTo(1);
		assertThat(column(INTERVIEW)).containsExactly(first, card, last);
	}

	@Test
	void updateTrimsTextAndTurnsBlankOptionalFieldsIntoNull() {
		JobApplication card = myCard("Acme", SAVED, 0);
		card.setLocation("Madrid");
		card.setNotes("Old notes");

		ApplicationResponse response = applicationService.update(USER_ID, card.getId(), new UpdateApplicationRequest(
				"  Acme Inc ", " Platform Engineer ", "  ", "", null, " 60k ", "\t", null));

		assertThat(response.company()).isEqualTo("Acme Inc");
		assertThat(response.roleTitle()).isEqualTo("Platform Engineer");
		assertThat(response.salaryNote()).isEqualTo("60k");
		assertThat(response.jobUrl()).isNull();
		assertThat(response.location()).isNull();
		assertThat(response.notes()).isNull();
	}

	@Test
	void updateMarksTheCardAsUpdatedNowAndLeavesItsNeighborsAlone() {
		JobApplication neighbor = myCard("Acme", SAVED, 0);
		JobApplication card = myCard("Globex", SAVED, 1);
		neighbor.touch(EARLIER);
		card.touch(EARLIER);
		ApplicationResponse neighborBefore = ApplicationResponse.from(neighbor);

		ApplicationResponse response = applicationService.update(USER_ID, card.getId(), updateRequest("Globex Corp"));

		assertThat(response.updatedAt()).isEqualTo(NOW);
		assertThat(ApplicationResponse.from(neighbor)).isEqualTo(neighborBefore);
	}

	@Test
	void updateOfAnAppliedCardWithoutADateKeepsTheDateItHad() {
		JobApplication card = myCard("Acme", APPLIED, 0);
		card.setAppliedAt(EXPLICIT_DATE);

		ApplicationResponse response = applicationService.update(USER_ID, card.getId(), updateRequest("Acme Corp"));

		assertThat(response.company()).isEqualTo("Acme Corp");
		assertThat(response.appliedAt()).isEqualTo(EXPLICIT_DATE);
	}

	@Test
	void updateOfAnAppliedCardCanChangeItsDate() {
		JobApplication card = myCard("Acme", APPLIED, 0);
		card.setAppliedAt(EXPLICIT_DATE);
		LocalDate corrected = LocalDate.of(2026, 2, 20);

		ApplicationResponse response = applicationService.update(USER_ID, card.getId(),
				new UpdateApplicationRequest("Acme", "Backend Developer", null, null, null, null, null, corrected));

		assertThat(response.appliedAt()).isEqualTo(corrected);
	}

	@Test
	void updateOutsideAppliedCanClearTheDate() {
		JobApplication card = myCard("Acme", SAVED, 0);
		card.setAppliedAt(EXPLICIT_DATE);

		ApplicationResponse response = applicationService.update(USER_ID, card.getId(), updateRequest("Acme"));

		assertThat(response.appliedAt()).isNull();
	}

	@Test
	void rejectsAnAppliedDateNoRealApplicationCouldHave() {
		JobApplication card = myCard("Acme", SAVED, 0);
		LocalDate tooOld = LocalDate.of(1969, 12, 31);
		LocalDate tooFarAhead = TODAY.plusYears(1).plusDays(1);

		assertThatThrownBy(() -> applicationService.create(USER_ID, createRequest(SAVED, tooOld)))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> applicationService.create(USER_ID, createRequest(SAVED, tooFarAhead)))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> applicationService.update(USER_ID, card.getId(),
				new UpdateApplicationRequest("Acme", "Backend Developer", null, null, null, null, null, tooOld)))
			.isInstanceOf(InvalidRequestException.class);

		// A date slightly ahead is allowed: the user's "today" can be ahead of the server's.
		assertThat(applicationService.create(USER_ID, createRequest(SAVED, TODAY.plusDays(1))).appliedAt())
			.isEqualTo(TODAY.plusDays(1));
	}

	@Test
	void removesTheNulCharacterFromText() {
		ApplicationResponse response = applicationService.create(USER_ID, new CreateApplicationRequest("Ac\u0000me",
				"Dev\u0000", null, "\u0000", null, null, "note\u0000s", null, null));

		assertThat(response.company()).isEqualTo("Acme");
		assertThat(response.roleTitle()).isEqualTo("Dev");
		assertThat(response.location()).isNull();
		assertThat(response.notes()).isEqualTo("notes");
	}

	@Test
	void everyWriteLocksTheCallersBoardAndReadsDoNot() {
		JobApplication card = myCard("Acme", SAVED, 0);

		applicationService.list(USER_ID);
		applicationService.get(USER_ID, card.getId());
		verify(repository, never()).lockBoard(any());

		applicationService.create(USER_ID, createRequest(SAVED, null));
		applicationService.update(USER_ID, card.getId(), updateRequest("Acme Corp"));
		applicationService.move(USER_ID, card.getId(), new MoveApplicationRequest(OFFER, 0));
		applicationService.delete(USER_ID, card.getId());

		verify(repository, times(4)).lockBoard(USER_ID);
	}

	@Test
	void updateOfAnotherUsersCardThrowsNotFoundAndChangesNothing() {
		myCard("Acme", SAVED, 0);
		JobApplication theirs = otherUsersCard("Umbrella", SAVED, 0);
		List<ApplicationResponse> before = snapshot();

		assertThatThrownBy(() -> applicationService.update(USER_ID, theirs.getId(), updateRequest("Hijacked")))
			.isInstanceOf(ApplicationNotFoundException.class);

		assertThat(snapshot()).isEqualTo(before);
	}

	// move

	@Test
	void moveDownInsideTheSameColumnReordersAndRenumbers() {
		JobApplication card = myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		myCard("Initech", SAVED, 2);
		myCard("Massive", SAVED, 3);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(SAVED, 2));

		assertThat(response.status()).isEqualTo(SAVED);
		assertThat(response.position()).isEqualTo(2);
		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Globex", 0), tuple("Initech", 1), tuple("Acme", 2), tuple("Massive", 3));
	}

	@Test
	void moveUpInsideTheSameColumnReordersAndRenumbers() {
		myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		myCard("Initech", SAVED, 2);
		JobApplication card = myCard("Massive", SAVED, 3);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(SAVED, 0));

		assertThat(response.position()).isZero();
		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Massive", 0), tuple("Acme", 1), tuple("Globex", 2), tuple("Initech", 3));
	}

	@Test
	void moveToThePlaceTheCardAlreadyHasKeepsTheOrder() {
		myCard("Acme", SAVED, 0);
		JobApplication card = myCard("Globex", SAVED, 1);
		myCard("Initech", SAVED, 2);

		applicationService.move(USER_ID, card.getId(), new MoveApplicationRequest(SAVED, 1));

		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Acme", 0), tuple("Globex", 1), tuple("Initech", 2));
	}

	@Test
	void movePastTheEndOfTheSameColumnMeansLast() {
		JobApplication card = myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		myCard("Initech", SAVED, 2);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(SAVED, 99));

		assertThat(response.position()).isEqualTo(2);
		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Globex", 0), tuple("Initech", 1), tuple("Acme", 2));
	}

	@Test
	void moveToAnotherColumnInsertsAtTheRequestedIndexAndClosesTheGap() {
		myCard("Acme", SAVED, 0);
		JobApplication card = myCard("Globex", SAVED, 1);
		myCard("Initech", SAVED, 2);
		myCard("Massive", INTERVIEW, 0);
		myCard("Hooli", INTERVIEW, 1);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(INTERVIEW, 1));

		assertThat(response.status()).isEqualTo(INTERVIEW);
		assertThat(response.position()).isEqualTo(1);
		assertThat(column(INTERVIEW)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Massive", 0), tuple("Globex", 1), tuple("Hooli", 2));
		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Acme", 0), tuple("Initech", 1));
	}

	@Test
	void moveToTheTopOfAnotherColumnPushesItsCardsDown() {
		JobApplication card = myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		myCard("Massive", OFFER, 0);
		myCard("Hooli", OFFER, 1);

		applicationService.move(USER_ID, card.getId(), new MoveApplicationRequest(OFFER, 0));

		assertThat(column(OFFER)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Acme", 0), tuple("Massive", 1), tuple("Hooli", 2));
		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Globex", 0));
	}

	@Test
	void moveToAnEmptyColumnPutsTheCardFirstAndCanEmptyTheOldColumn() {
		JobApplication card = myCard("Acme", SAVED, 0);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(REJECTED, 0));

		assertThat(response.status()).isEqualTo(REJECTED);
		assertThat(response.position()).isZero();
		assertThat(column(REJECTED)).containsExactly(card);
		assertThat(column(SAVED)).isEmpty();
	}

	@Test
	void movePastTheEndOfAnotherColumnMeansLast() {
		JobApplication card = myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		myCard("Massive", INTERVIEW, 0);
		myCard("Hooli", INTERVIEW, 1);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(INTERVIEW, 50));

		assertThat(response.position()).isEqualTo(2);
		assertThat(column(INTERVIEW)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Massive", 0), tuple("Hooli", 1), tuple("Acme", 2));
		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Globex", 0));
	}

	@Test
	void moveRenumbersColumnsThatHadDuplicateOrMissingPositions() {
		// What two simultaneous requests can leave behind: a tie in one column, a hole in the other.
		myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 0);
		JobApplication last = myCard("Initech", SAVED, 5);
		JobApplication card = myCard("Massive", APPLIED, 2);
		myCard("Hooli", APPLIED, 4);
		myCard("Vandelay", APPLIED, 7);

		applicationService.move(USER_ID, card.getId(), new MoveApplicationRequest(SAVED, 1));

		assertThat(column(SAVED)).extracting(JobApplication::getPosition).containsExactly(0, 1, 2, 3);
		assertThat(card.getPosition()).isEqualTo(1);
		assertThat(last.getPosition()).isEqualTo(3);
		assertThat(column(APPLIED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Hooli", 0), tuple("Vandelay", 1));
	}

	@Test
	void moveToAppliedStampsTodaysDateWhenTheCardHasNone() {
		JobApplication card = myCard("Acme", SAVED, 0);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(APPLIED, 0));

		assertThat(response.status()).isEqualTo(APPLIED);
		assertThat(response.appliedAt()).isEqualTo(TODAY);
	}

	@Test
	void moveToAppliedKeepsTheDateTheCardAlreadyHas() {
		JobApplication card = myCard("Acme", SAVED, 0);
		card.setAppliedAt(EXPLICIT_DATE);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(APPLIED, 0));

		assertThat(response.appliedAt()).isEqualTo(EXPLICIT_DATE);
	}

	@Test
	void moveToAColumnOtherThanAppliedDoesNotStampADate() {
		JobApplication card = myCard("Acme", SAVED, 0);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(REJECTED, 0));

		assertThat(response.appliedAt()).isNull();
	}

	@Test
	void moveOutOfAppliedKeepsTheDate() {
		JobApplication card = myCard("Acme", APPLIED, 0);
		card.setAppliedAt(EXPLICIT_DATE);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(INTERVIEW, 0));

		assertThat(response.status()).isEqualTo(INTERVIEW);
		assertThat(response.appliedAt()).isEqualTo(EXPLICIT_DATE);
	}

	@Test
	void moveMarksOnlyTheMovedCardAsUpdated() {
		JobApplication card = myCard("Acme", SAVED, 0);
		JobApplication leftBehind = myCard("Globex", SAVED, 1);
		JobApplication pushedDown = myCard("Massive", INTERVIEW, 0);
		card.touch(EARLIER);
		leftBehind.touch(EARLIER);
		pushedDown.touch(EARLIER);

		ApplicationResponse response = applicationService.move(USER_ID, card.getId(),
				new MoveApplicationRequest(INTERVIEW, 0));

		assertThat(response.updatedAt()).isEqualTo(NOW);
		// Both neighbors were renumbered, which must not make them look recently updated.
		assertThat(leftBehind.getPosition()).isZero();
		assertThat(pushedDown.getPosition()).isEqualTo(1);
		assertThat(leftBehind.getUpdatedAt()).isEqualTo(EARLIER);
		assertThat(pushedDown.getUpdatedAt()).isEqualTo(EARLIER);
	}

	@Test
	void moveNeverTouchesAnotherUsersCards() {
		JobApplication card = myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		myCard("Massive", INTERVIEW, 0);
		JobApplication theirSaved = otherUsersCard("Umbrella", SAVED, 1);
		JobApplication theirInterview = otherUsersCard("Cyberdyne", INTERVIEW, 0);
		ApplicationResponse theirSavedBefore = ApplicationResponse.from(theirSaved);
		ApplicationResponse theirInterviewBefore = ApplicationResponse.from(theirInterview);

		applicationService.move(USER_ID, card.getId(), new MoveApplicationRequest(INTERVIEW, 0));

		assertThat(ApplicationResponse.from(theirSaved)).isEqualTo(theirSavedBefore);
		assertThat(ApplicationResponse.from(theirInterview)).isEqualTo(theirInterviewBefore);
	}

	@Test
	void moveOfAnotherUsersCardThrowsNotFoundAndChangesNothing() {
		myCard("Acme", SAVED, 0);
		myCard("Globex", APPLIED, 0);
		JobApplication theirs = otherUsersCard("Umbrella", SAVED, 0);
		otherUsersCard("Cyberdyne", SAVED, 1);
		List<ApplicationResponse> before = snapshot();

		assertThatThrownBy(
				() -> applicationService.move(USER_ID, theirs.getId(), new MoveApplicationRequest(APPLIED, 0)))
			.isInstanceOf(ApplicationNotFoundException.class);

		assertThat(snapshot()).isEqualTo(before);
	}

	// delete

	@Test
	void deleteRemovesTheCardAndRenumbersTheRestOfTheColumn() {
		myCard("Acme", SAVED, 0);
		JobApplication card = myCard("Globex", SAVED, 1);
		myCard("Initech", SAVED, 2);
		myCard("Massive", SAVED, 3);

		applicationService.delete(USER_ID, card.getId());

		assertThat(board).doesNotContain(card);
		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Acme", 0), tuple("Initech", 1), tuple("Massive", 2));
	}

	@Test
	void deleteOfTheLastCardOfAColumnLeavesTheOthersInPlace() {
		myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		JobApplication card = myCard("Initech", SAVED, 2);

		applicationService.delete(USER_ID, card.getId());

		assertThat(column(SAVED)).extracting(JobApplication::getCompany, JobApplication::getPosition)
			.containsExactly(tuple("Acme", 0), tuple("Globex", 1));
	}

	@Test
	void deleteOfTheOnlyCardOfAColumnLeavesItEmpty() {
		JobApplication card = myCard("Acme", OFFER, 0);

		applicationService.delete(USER_ID, card.getId());

		assertThat(board).isEmpty();
	}

	@Test
	void deleteLeavesOtherColumnsAndOtherUsersCardsUntouched() {
		JobApplication card = myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		JobApplication otherColumn = myCard("Massive", APPLIED, 1);
		JobApplication theirs = otherUsersCard("Umbrella", SAVED, 1);
		ApplicationResponse otherColumnBefore = ApplicationResponse.from(otherColumn);
		ApplicationResponse theirsBefore = ApplicationResponse.from(theirs);

		applicationService.delete(USER_ID, card.getId());

		assertThat(ApplicationResponse.from(otherColumn)).isEqualTo(otherColumnBefore);
		assertThat(ApplicationResponse.from(theirs)).isEqualTo(theirsBefore);
	}

	@Test
	void deleteOfAnotherUsersCardThrowsNotFoundAndChangesNothing() {
		myCard("Acme", SAVED, 0);
		JobApplication theirs = otherUsersCard("Umbrella", SAVED, 0);
		otherUsersCard("Cyberdyne", SAVED, 1);
		List<ApplicationResponse> before = snapshot();

		assertThatThrownBy(() -> applicationService.delete(USER_ID, theirs.getId()))
			.isInstanceOf(ApplicationNotFoundException.class);

		assertThat(snapshot()).isEqualTo(before);
		verify(repository, never()).delete(any(JobApplication.class));
	}

	@Test
	void deleteOfACardThatDoesNotExistThrowsNotFoundAndChangesNothing() {
		myCard("Acme", SAVED, 0);
		myCard("Globex", SAVED, 1);
		List<ApplicationResponse> before = snapshot();

		assertThatThrownBy(() -> applicationService.delete(USER_ID, UUID.randomUUID()))
			.isInstanceOf(ApplicationNotFoundException.class);

		assertThat(snapshot()).isEqualTo(before);
		verify(repository, never()).delete(any(JobApplication.class));
	}

	// helpers

	private JobApplication myCard(String company, ApplicationStatus status, int position) {
		return cardOnBoard(USER_ID, company, status, position);
	}

	private JobApplication otherUsersCard(String company, ApplicationStatus status, int position) {
		return cardOnBoard(OTHER_USER_ID, company, status, position);
	}

	private JobApplication cardOnBoard(UUID userId, String company, ApplicationStatus status, int position) {
		JobApplication card = new JobApplication(userId, status, position);
		// The id is normally assigned by JPA when the entity is persisted.
		ReflectionTestUtils.setField(card, "id", UUID.randomUUID());
		card.setCompany(company);
		card.setRoleTitle("Backend Developer");
		board.add(card);
		return card;
	}

	/** The caller's cards of one column, in position order. */
	private List<JobApplication> column(ApplicationStatus status) {
		return cardsIn(USER_ID, status);
	}

	private List<JobApplication> cardsIn(UUID userId, ApplicationStatus status) {
		return inPositionOrder(board.stream()
			.filter(card -> card.getUserId().equals(userId) && card.getStatus() == status)
			.toList());
	}

	/** Ties keep the board order, which stands in for the creation time the real query sorts by. */
	private static List<JobApplication> inPositionOrder(List<JobApplication> cards) {
		List<JobApplication> ordered = new ArrayList<>(cards);
		ordered.sort(Comparator.comparingInt(JobApplication::getPosition));
		return ordered;
	}

	/** The state of every card of every user, to prove that a rejected request changed nothing. */
	private List<ApplicationResponse> snapshot() {
		return board.stream().map(ApplicationResponse::from).toList();
	}

	private static CreateApplicationRequest createRequest(ApplicationStatus status, LocalDate appliedAt) {
		return new CreateApplicationRequest("Hooli", "Backend Developer", null, null, null, null, null, status,
				appliedAt);
	}

	private static UpdateApplicationRequest updateRequest(String company) {
		return new UpdateApplicationRequest(company, "Backend Developer", null, null, null, null, null, null);
	}

}
