import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { toErrorMessage } from '../core/api-error';
import {
  ApplicationDetails,
  ApplicationStatus,
  BOARD_COLUMNS,
  BoardColumnDefinition,
  CreateApplicationRequest,
  JobApplication,
} from './application.models';
import { ApplicationsApi } from './applications.api';
import { cardsIn, moveCard, removeCard } from './board-order';

export interface BoardColumn extends BoardColumnDefinition {
  cards: JobApplication[];
}

/**
 * State of the board: the user's cards in a signal, plus the operations that change them.
 * Provided by the Board component (not in root), so every visit to the board starts with
 * fresh state and nothing is left over after logout.
 */
@Injectable()
export class BoardStore {
  private readonly api = inject(ApplicationsApi);
  private readonly cards = signal<readonly JobApplication[]>([]);

  readonly loading = signal(true);
  /** Set when the board could not be loaded at all. */
  readonly loadError = signal<string | null>(null);
  /** Set when a single action (such as a move) failed; the board itself is still usable. */
  readonly actionError = signal<string | null>(null);

  readonly columns = computed<BoardColumn[]>(() =>
    BOARD_COLUMNS.map((column) => ({ ...column, cards: cardsIn(this.cards(), column.status) })),
  );
  readonly total = computed(() => this.cards().length);

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.list().subscribe({
      next: (cards) => {
        this.cards.set(cards);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(toErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  create(request: CreateApplicationRequest): Observable<JobApplication> {
    return this.api.create(request).pipe(tap((created) => this.cards.update((cards) => [...cards, created])));
  }

  update(id: string, details: ApplicationDetails): Observable<JobApplication> {
    return this.api.update(id, details).pipe(tap((updated) => this.replace(updated)));
  }

  remove(id: string): Observable<void> {
    return this.api.remove(id).pipe(tap(() => this.cards.update((cards) => removeCard(cards, id))));
  }

  /**
   * Optimistic move: the card jumps to its new place immediately, and the request is sent
   * in the background. If the server rejects it, the board goes back to how it was and is
   * then re-read from the server, which stays the source of truth.
   */
  move(id: string, toStatus: ApplicationStatus, toIndex: number): void {
    const before = this.cards();
    const after = moveCard(before, id, toStatus, toIndex);
    if (after === before) {
      return;
    }
    this.cards.set(after);
    this.actionError.set(null);

    const position = cardsIn(after, toStatus).findIndex((card) => card.id === id);
    this.api.move(id, { status: toStatus, position }).subscribe({
      next: (saved) => this.replace(saved),
      error: (error: unknown) => {
        this.cards.set(before);
        this.actionError.set(`The card could not be moved. ${toErrorMessage(error)}`);
        this.resync();
      },
    });
  }

  dismissActionError(): void {
    this.actionError.set(null);
  }

  private replace(card: JobApplication): void {
    this.cards.update((cards) => cards.map((item) => (item.id === card.id ? card : item)));
  }

  /** Quietly re-reads the board; if that fails too, the rolled-back state stays on screen. */
  private resync(): void {
    this.api.list().subscribe({ next: (cards) => this.cards.set(cards), error: () => undefined });
  }
}
