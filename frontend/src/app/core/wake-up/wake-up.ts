import { HttpInterceptorFn } from '@angular/common/http';
import { Component, Injectable, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

/** A request slower than this is probably waiting for a sleeping free-tier server. */
export const SLOW_REQUEST_MS = 4000;

/**
 * Tracks requests that take long enough to suggest the API server is waking up. The public
 * demo runs on free hosting that sleeps when idle; its first request can take a minute, and
 * without a word the app would just look broken.
 */
@Injectable({ providedIn: 'root' })
export class WakeUpNotice {
  private readonly slowRequests = signal(0);

  readonly visible = computed(() => this.slowRequests() > 0);

  /** Call when a request starts; returns the function to call when it ends. */
  track(): () => void {
    let slow = false;
    const timer = setTimeout(() => {
      slow = true;
      this.slowRequests.update((count) => count + 1);
    }, SLOW_REQUEST_MS);
    return () => {
      clearTimeout(timer);
      if (slow) {
        this.slowRequests.update((count) => count - 1);
      }
    };
  }
}

export const wakeUpInterceptor: HttpInterceptorFn = (request, next) => {
  const done = inject(WakeUpNotice).track();
  return next(request).pipe(finalize(done));
};

@Component({
  selector: 'app-wake-up-banner',
  template: `
    @if (notice.visible()) {
      <p class="wake-up" role="status" aria-live="polite">
        The demo server is waking up after a quiet period (free hosting). This can take up to a
        minute, only the first time.
      </p>
    }
  `,
  styles: `
    .wake-up {
      position: fixed;
      left: 50%;
      bottom: 1rem;
      z-index: 1000;
      max-width: min(36rem, calc(100% - 2rem));
      margin: 0;
      padding: 0.75rem 1rem;
      transform: translateX(-50%);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      background: var(--color-surface);
      box-shadow: var(--shadow-card);
      font-size: 0.875rem;
      color: var(--color-text);
    }
  `,
})
export class WakeUpBanner {
  protected readonly notice = inject(WakeUpNotice);
}
