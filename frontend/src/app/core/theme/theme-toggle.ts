import { Component, computed, inject } from '@angular/core';
import { ThemeService } from './theme.service';

/** Icon button that switches between the dark and light themes. */
@Component({
  selector: 'app-theme-toggle',
  template: `
    <button
      class="icon-btn theme-toggle"
      type="button"
      [attr.aria-label]="label()"
      [attr.title]="label()"
      (click)="theme.toggle()"
    >
      @if (theme.theme() === 'dark') {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path
            d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
          />
        </svg>
      } @else {
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z" />
        </svg>
      }
    </button>
  `,
  styles: `
    .theme-toggle {
      width: 32px;
      height: 32px;
      border-color: var(--color-border);
    }

    svg {
      fill: none;
      stroke: currentcolor;
      stroke-width: 1.8;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
  `,
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);
  protected readonly label = computed(() =>
    this.theme.theme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme',
  );
}
