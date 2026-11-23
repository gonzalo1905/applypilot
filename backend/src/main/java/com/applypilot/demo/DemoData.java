package com.applypilot.demo;

import com.applypilot.application.ApplicationStatus;
import com.applypilot.application.WorkMode;
import java.time.LocalDate;
import java.util.List;

/** The fictitious content of the demo account. Every name, company and figure is invented. */
final class DemoData {

	static final String CV_FILE_NAME = "alex-rivera-cv.pdf";

	static final List<String> CV_LINES = List.of(
			"Alex Rivera - Backend Developer",
			"Lima, Peru. Fictitious profile for the ApplyPilot demo.",
			"",
			"Summary",
			"Backend developer with 5 years of experience building REST APIs in Java and Spring Boot.",
			"",
			"Experience",
			"Andes Fintech (2022-2025): Backend Developer. Built payment APIs with Java 21, Spring Boot and",
			"PostgreSQL. Moved nightly reconciliation to Kafka consumers, cutting processing time by 40%.",
			"Containerized services with Docker and deployed them with GitHub Actions.",
			"Retail Cloud (2020-2022): Junior Java Developer. Maintained an order API, wrote JUnit tests",
			"and improved slow SQL queries.",
			"",
			"Skills",
			"Java, Spring Boot, PostgreSQL, Kafka, Docker, Git, JUnit, REST APIs, Angular (basic)",
			"",
			"Education",
			"Systems Engineering, Universidad Nacional (2019)",
			"Languages: Spanish (native), English (B2)");

	/** A demo card; {@code posting} is analyzed at seeding time when present. */
	record Card(String company, String roleTitle, ApplicationStatus status, WorkMode workMode, String location,
			LocalDate appliedAt, String notes, String posting) {
	}

	static List<Card> cards(LocalDate today) {
		return List.of(
				new Card("Northwind Payments", "Senior Backend Engineer", ApplicationStatus.APPLIED, WorkMode.REMOTE,
						"Remote (EU time zones)", today.minusDays(6), "Referred by a former teammate.", """
								Northwind Payments is hiring a Senior Backend Engineer (remote, EU time zones).
								You will design REST APIs and event-driven services for our card platform.
								Requirements: 5+ years with Java, Spring Boot, PostgreSQL and Docker. Experience with Kafka.
								Nice to have: Kubernetes, Terraform.
								We offer EUR 70,000 - 85,000 per year."""),
				new Card("Pagos del Sur", "Desarrollador Backend Semi Senior", ApplicationStatus.SAVED,
						WorkMode.HYBRID, "Lima, Peru", null, "Posting in Spanish: the letter should be too.", """
								Pagos del Sur busca un Desarrollador Backend Semi Senior para su equipo de cobranzas, \
								modalidad híbrida en Lima.
								Requisitos: 3 años de experiencia con Java y Spring Boot, bases de datos PostgreSQL, \
								APIs REST y Git.
								Deseable: experiencia con RabbitMQ y Docker.
								Ofrecemos sueldo competitivo y capacitaciones."""),
				new Card("Brightlane", "Frontend Developer (Angular)", ApplicationStatus.SAVED, WorkMode.REMOTE,
						"Remote (Americas)", null, "Stretch role: more frontend than my CV shows.", """
								Brightlane is looking for a Frontend Developer to build our customer portal.
								You must have: Angular, TypeScript, RxJS, Cypress and GraphQL.
								It would be a plus to know Storybook and Figma.
								Fully remote within the Americas."""),
				new Card("CloudNest", "Platform Engineer", ApplicationStatus.INTERVIEW, WorkMode.HYBRID, "Bogota",
						today.minusDays(14), "Technical interview on Thursday.", null),
				new Card("Quanta Logistics", "Java Developer", ApplicationStatus.REJECTED, WorkMode.ONSITE, "Austin",
						today.minusDays(30), "They chose a candidate with logistics experience.", null));
	}

	private DemoData() {
	}

}
