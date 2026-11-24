import { Component, input } from '@angular/core';

/**
 * The ApplyPilot mark (an "A" drawn as a climbing route on an amber tile) and the wordmark.
 * The mark keeps its own colours in both themes, like the favicon.
 */
@Component({
  selector: 'app-logo',
  template: `
    <span class="logo" [class.logo-lg]="size() === 'lg'">
      <svg class="mark" viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="8" fill="#FF8A3D" />
        <path
          d="M8.5 23.5 16 8l7.5 15.5"
          fill="none"
          stroke="#14100C"
          stroke-width="3.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path d="M11.6 18h8.8" stroke="#14100C" stroke-width="3.2" stroke-linecap="round" />
      </svg>
      <span class="word">apply<span>pilot</span></span>
    </span>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .logo {
      display: inline-flex;
      align-items: center;
      gap: 0.55rem;
      color: var(--color-text);
    }

    .mark {
      display: block;
      width: 28px;
      height: 28px;
    }

    .word {
      font-size: 1.125rem;
      font-weight: 600;
      letter-spacing: -0.04em;

      span {
        color: var(--color-text-muted);
      }
    }

    .logo-lg {
      .mark {
        width: 34px;
        height: 34px;
      }

      .word {
        font-size: 1.375rem;
      }
    }
  `,
})
export class Logo {
  readonly size = input<'md' | 'lg'>('md');
}
