export type ApplicationStatus = 'SAVED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED';

export type WorkMode = 'REMOTE' | 'HYBRID' | 'ONSITE';

/** A card on the board, as returned by the API. */
export interface JobApplication {
  id: string;
  company: string;
  roleTitle: string;
  jobUrl: string | null;
  location: string | null;
  workMode: WorkMode | null;
  salaryNote: string | null;
  notes: string | null;
  status: ApplicationStatus;
  /** Zero-based order inside its column. */
  position: number;
  /** ISO date (yyyy-MM-dd). */
  appliedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** The editable fields of a card (body of the update request). */
export interface ApplicationDetails {
  company: string;
  roleTitle: string;
  jobUrl: string | null;
  location: string | null;
  workMode: WorkMode | null;
  salaryNote: string | null;
  notes: string | null;
  appliedAt: string | null;
}

export interface CreateApplicationRequest extends ApplicationDetails {
  status: ApplicationStatus;
}

export interface MoveApplicationRequest {
  status: ApplicationStatus;
  position: number;
}

export interface BoardColumnDefinition {
  status: ApplicationStatus;
  title: string;
  emptyHint: string;
}

/** The Kanban columns, in pipeline order. */
export const BOARD_COLUMNS: readonly BoardColumnDefinition[] = [
  { status: 'SAVED', title: 'Saved', emptyHint: 'Roles you want to apply to.' },
  { status: 'APPLIED', title: 'Applied', emptyHint: 'Applications you have sent.' },
  { status: 'INTERVIEW', title: 'Interview', emptyHint: 'Processes with interviews scheduled.' },
  { status: 'OFFER', title: 'Offer', emptyHint: 'Offers you have received.' },
  { status: 'REJECTED', title: 'Rejected', emptyHint: 'Closed processes, kept for reference.' },
];

export const WORK_MODE_LABELS: Record<WorkMode, string> = {
  REMOTE: 'Remote',
  HYBRID: 'Hybrid',
  ONSITE: 'On-site',
};
