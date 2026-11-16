package com.applypilot.application.dto;

import com.applypilot.application.WorkMode;
import java.time.LocalDate;

/**
 * The editable fields shared by the create and update requests, so the service can apply
 * both with one method.
 */
public interface ApplicationDetails {

	String company();

	String roleTitle();

	String jobUrl();

	String location();

	WorkMode workMode();

	String salaryNote();

	String notes();

	LocalDate appliedAt();

}
