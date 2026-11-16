import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { Topbar } from '../core/topbar/topbar';
import { ApplicationDialog } from './application-dialog/application-dialog';
import { ApplicationStatus, JobApplication, WORK_MODE_LABELS } from './application.models';
import { BoardStore } from './board.store';

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

  protected readonly workModeLabels = WORK_MODE_LABELS;
  protected readonly dialog = signal<DialogState | null>(null);

  constructor() {
    // Confirms with the API that the stored token is still valid and refreshes the profile.
    // A 401 is handled by the interceptor, which logs the user out.
    this.auth.refreshUser().subscribe({ error: () => undefined });
    this.store.load();
  }

  protected openCreate(status: ApplicationStatus = 'SAVED'): void {
    this.dialog.set({ application: null, status });
  }

  protected openEdit(application: JobApplication): void {
    this.dialog.set({ application, status: application.status });
  }

  protected closeDialog(): void {
    this.dialog.set(null);
  }

  /** A card was dropped: the drop list's data is the column, the item's data is the card. */
  protected drop(event: CdkDragDrop<ApplicationStatus, ApplicationStatus, JobApplication>): void {
    if (event.previousContainer === event.container && event.previousIndex === event.currentIndex) {
      return;
    }
    this.store.move(event.item.data.id, event.container.data, event.currentIndex);
  }
}
