import { ApplicationStatus, JobApplication } from '../application.models';

let sequence = 0;

/** Builds a card for tests. Each call gets a later creation time, like real inserts. */
export function card(
  id: string,
  status: ApplicationStatus,
  position: number,
  overrides: Partial<JobApplication> = {},
): JobApplication {
  sequence += 1;
  const createdAt = new Date(Date.UTC(2026, 0, 1, 0, 0, sequence)).toISOString();
  return {
    id,
    company: `Company ${id}`,
    roleTitle: 'Developer',
    jobUrl: null,
    location: null,
    workMode: null,
    salaryNote: null,
    notes: null,
    status,
    position,
    appliedAt: null,
    createdAt,
    updatedAt: createdAt,
    ...overrides,
  };
}

/** "id:position" of every card in a column, in order: compact to assert on. */
export function layout(cards: readonly JobApplication[], status: ApplicationStatus): string[] {
  return cards
    .filter((item) => item.status === status)
    .sort((a, b) => a.position - b.position)
    .map((item) => `${item.id}:${item.position}`);
}
