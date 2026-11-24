import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { Topbar } from '../core/topbar/topbar';
import { ApplicationDialog } from './application-dialog/application-dialog';
import { ApplicationStatus, JobApplication, WORK_MODE_LABELS } from './application.models';
import { BoardStore } from './board.store';
import { BoardInsights, InsightsApi } from './insights.api';

/** What the dialog is editing: an existing card, or a new one for a given column. */
interface DialogState {
  application: JobApplication | null;
  status: ApplicationStatus;
}

@Component({
  selector: 'app-board',
  imports: [CdkDropListGroup, CdkDropList, CdkDrag, DatePipe, Topbar, ApplicationDialog],
  providers: [BoardStore],
  templateUrl: './board.html',
  styleUrl: './board.scss',
})
export class Board {
  private readonly auth = inject(AuthService);
  protected readonly store = inject(BoardStore);

  private readonly insightsApi = inject(InsightsApi);

  protected readonly workModeLabels = WORK_MODE_LABELS;
  protected readonly dialog = signal<DialogState | null>(null);
  /** Scores and AI allowance; null until loaded (or if that failed: the board works without). */
  protected readonly insights = signal<BoardInsights | null>(null);
  protected readonly week = isoWeek(new Date());
  protected readonly skeletonColumns = [[1, 2, 3], [1, 2], [1], [], [1]];

  /** Applications still in play: saved, applied or interviewing. */
  protected readonly active = computed(() =>
    this.store
      .columns()
      .filter((column) => ['SAVED', 'APPLIED', 'INTERVIEW'].includes(column.status))
      .reduce((sum, column) => sum + column.cards.length, 0),
  );
  protected readonly bestMatch = computed(() => {
    const scores = Object.values(this.insights()?.matchScores ?? {});
    return scores.length > 0 ? Math.max(...scores) : null;
  });

  constructor() {
    // Confirms with the API that the stored token is still valid and refreshes the profile.
    // A 401 is handled by the interceptor, which logs the user out.
    this.auth.refreshUser().subscribe({ error: () => undefined });
    this.store.load();
    this.loadInsights();
  }

  protected scoreOf(application: JobApplication): number | null {
    return this.insights()?.matchScores[application.id] ?? null;
  }

  /** The card is a button, so its label must carry everything a screen reader should hear. */
  protected cardLabel(application: JobApplication): string {
    const score = this.scoreOf(application);
    const label = `Edit ${application.roleTitle} at ${application.company}`;
    return score === null ? label : `${label}, match score ${score} of 100`;
  }

  protected openCreate(status: ApplicationStatus = 'SAVED'): void {
    this.dialog.set({ application: null, status });
  }

  protected openEdit(application: JobApplication): void {
    this.dialog.set({ application, status: application.status });
  }

  protected closeDialog(): void {
    this.dialog.set(null);
    // A match may have run inside the dialog: refresh scores and the AI allowance.
    this.loadInsights();
  }

  private loadInsights(): void {
    this.insightsApi.get().subscribe({
      next: (insights) => this.insights.set(insights),
      error: () => undefined,
    });
  }

  /** A card was dropped: the drop list's data is the column, the item's data is the card. */
  protected drop(event: CdkDragDrop<ApplicationStatus, ApplicationStatus, JobApplication>): void {
    if (event.previousContainer === event.container && event.previousIndex === event.currentIndex) {
      return;
    }
    this.store.move(event.item.data.id, event.container.data, event.currentIndex);
  }
}

/** ISO-8601 week number, for the "flight plan" eyebrow. */
export function isoWeek(date: Date): number {
  const day = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const weekday = day.getUTCDay() || 7;
  day.setUTCDate(day.getUTCDate() + 4 - weekday);
  const yearStart = new Date(Date.UTC(day.getUTCFullYear(), 0, 1));
  return Math.ceil(((day.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7);
}
