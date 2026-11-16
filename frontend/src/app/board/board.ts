import { Component, computed, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

interface BoardColumn {
  /** Stable id, also used as the CSS modifier for the column's accent color. */
  id: 'saved' | 'applied' | 'interview' | 'offer' | 'rejected';
  title: string;
  emptyHint: string;
}

const COLUMNS: readonly BoardColumn[] = [
  { id: 'saved', title: 'Saved', emptyHint: 'Roles you want to apply to.' },
  { id: 'applied', title: 'Applied', emptyHint: 'Applications you have sent.' },
  { id: 'interview', title: 'Interview', emptyHint: 'Processes with interviews scheduled.' },
  { id: 'offer', title: 'Offer', emptyHint: 'Offers you have received.' },
  { id: 'rejected', title: 'Rejected', emptyHint: 'Closed processes, kept for reference.' },
];

@Component({
  selector: 'app-board',
  templateUrl: './board.html',
  styleUrl: './board.scss',
})
export class Board {
  private readonly auth = inject(AuthService);

  protected readonly columns = COLUMNS;
  protected readonly user = this.auth.user;
  protected readonly initials = computed(() =>
    (this.user()?.fullName ?? '')
      .split(/\s+/)
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join(''),
  );

  constructor() {
    // Confirms with the API that the stored token is still valid and refreshes the profile.
    // A 401 is handled by the interceptor, which logs the user out.
    this.auth.refreshUser().subscribe({ error: () => undefined });
  }

  protected logout(): void {
    this.auth.logout();
  }
}
