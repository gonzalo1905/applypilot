import { ApplicationStatus, JobApplication } from './application.models';

/**
 * Pure functions for the order of cards. They mirror the backend rules (positions are
 * 0..n-1 inside each column), so the board can show the result of a drag immediately
 * and the server's answer only confirms it.
 */

/** Order inside a column: by position, and by creation time when two positions tie. */
export function byPosition(a: JobApplication, b: JobApplication): number {
  return a.position - b.position || a.createdAt.localeCompare(b.createdAt);
}

/** The cards of one column, in order. */
export function cardsIn(cards: readonly JobApplication[], status: ApplicationStatus): JobApplication[] {
  return cards.filter((card) => card.status === status).sort(byPosition);
}

/**
 * Returns the list as it looks after moving one card to `toIndex` of the `toStatus` column.
 * The destination column (and the source column, if different) is renumbered 0..n-1.
 * An index past the end means "last". Returns the same array when nothing changes.
 */
export function moveCard(
  cards: readonly JobApplication[],
  id: string,
  toStatus: ApplicationStatus,
  toIndex: number,
): readonly JobApplication[] {
  const card = cards.find((candidate) => candidate.id === id);
  if (card === undefined) {
    return cards;
  }

  const target = cardsIn(cards, toStatus).filter((candidate) => candidate.id !== id);
  const index = Math.max(0, Math.min(toIndex, target.length));
  const currentIndex = cardsIn(cards, card.status).findIndex((candidate) => candidate.id === id);
  if (card.status === toStatus && currentIndex === index) {
    return cards;
  }

  target.splice(index, 0, { ...card, status: toStatus });
  const changed = new Map<string, JobApplication>();
  target.forEach((item, position) => changed.set(item.id, { ...item, position }));
  if (card.status !== toStatus) {
    cardsIn(cards, card.status)
      .filter((candidate) => candidate.id !== id)
      .forEach((item, position) => changed.set(item.id, { ...item, position }));
  }
  return cards.map((item) => changed.get(item.id) ?? item);
}

/** Returns the list without one card, closing the gap it leaves in its column. */
export function removeCard(cards: readonly JobApplication[], id: string): readonly JobApplication[] {
  const card = cards.find((candidate) => candidate.id === id);
  if (card === undefined) {
    return cards;
  }
  const remaining = cards.filter((candidate) => candidate.id !== id);
  const renumbered = new Map<string, JobApplication>();
  cardsIn(remaining, card.status).forEach((item, position) =>
    renumbered.set(item.id, { ...item, position }),
  );
  return remaining.map((item) => renumbered.get(item.id) ?? item);
}
