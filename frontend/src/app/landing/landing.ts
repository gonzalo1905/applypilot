import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { toErrorMessage } from '../core/api-error';
import { Logo } from '../core/brand/logo';
import { ThemeToggle } from '../core/theme/theme-toggle';

interface PreviewCard {
  initial: string;
  company: string;
  role: string;
  score: number | null;
  tags: string[];
}

interface PreviewColumn {
  title: string;
  status: string;
  cards: PreviewCard[];
}

/** Public home page: what ApplyPilot does, with a one-click demo. */
@Component({
  selector: 'app-landing',
  imports: [RouterLink, Logo, ThemeToggle],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly signedIn = this.auth.isAuthenticated;
  protected readonly startingDemo = signal(false);
  protected readonly demoError = signal<string | null>(null);
  protected readonly repoUrl = 'https://github.com/gonzalo1905/applypilot';

  /** A static, fictitious board for the hero preview. */
  protected readonly preview: PreviewColumn[] = [
    {
      title: 'Saved',
      status: 'SAVED',
      cards: [
        {
          initial: 'B',
          company: 'Brightlane',
          role: 'Frontend Developer',
          score: 58,
          tags: ['Remote'],
        },
        {
          initial: 'P',
          company: 'Pagos del Sur',
          role: 'Backend Developer',
          score: null,
          tags: ['Lima', 'Hybrid'],
        },
      ],
    },
    {
      title: 'Applied',
      status: 'APPLIED',
      cards: [
        {
          initial: 'N',
          company: 'Northwind Payments',
          role: 'Senior Backend Engineer',
          score: 83,
          tags: ['Remote', 'EUR 70-85k'],
        },
        {
          initial: 'A',
          company: 'Atlas Health',
          role: 'Java Engineer',
          score: 74,
          tags: ['Remote'],
        },
      ],
    },
    {
      title: 'Interview',
      status: 'INTERVIEW',
      cards: [
        {
          initial: 'C',
          company: 'CloudNest',
          role: 'Platform Engineer',
          score: 67,
          tags: ['Hybrid'],
        },
      ],
    },
    {
      title: 'Offer',
      status: 'OFFER',
      cards: [],
    },
  ];

  protected tryDemo(): void {
    if (this.signedIn()) {
      void this.router.navigateByUrl('/board');
      return;
    }
    this.startingDemo.set(true);
    this.demoError.set(null);
    this.auth.loginAsDemo().subscribe({
      next: () => void this.router.navigateByUrl('/board'),
      error: (error: unknown) => {
        this.demoError.set(toErrorMessage(error));
        this.startingDemo.set(false);
      },
    });
  }
}
