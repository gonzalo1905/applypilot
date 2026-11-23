import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { toErrorMessage } from '../../../core/api-error';
import { MatchApi } from '../match.api';
import {
  AGENT_LABELS,
  AgentRun,
  REASON_LABELS,
  RUN_ERROR_LABELS,
  SOURCE_LABELS,
  isActive,
} from '../match.models';

/** How often a running run is polled. */
export const POLL_INTERVAL_MS = 1500;

/** Length of the gauge arc; the visible part is score% of it. */
const GAUGE_LENGTH = 100;

/**
 * "Match & cover letter": starts a run of the agent team for the application and shows its
 * result: the match score and skills, the cover letter with a copy button, the reviewer's
 * notes, and the timeline of steps. A run works in the background, so the panel polls until
 * it ends.
 */
@Component({
  selector: 'app-match-panel',
  templateUrl: './match-panel.html',
  styleUrl: './match-panel.scss',
})
export class MatchPanel implements OnInit {
  private readonly api = inject(MatchApi);

  readonly applicationId = input.required<string>();

  protected readonly agentLabels = AGENT_LABELS;
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly reasonLabels = REASON_LABELS;
  protected readonly gaugeLength = GAUGE_LENGTH;

  protected readonly run = signal<AgentRun | null>(null);
  protected readonly loading = signal(true);
  protected readonly starting = signal(false);
  protected readonly copied = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly active = computed(() => {
    const run = this.run();
    return run !== null && isActive(run);
  });
  protected readonly scoreLevel = computed(() => {
    const score = this.run()?.match?.score ?? 0;
    return score >= 70 ? 'high' : score >= 40 ? 'medium' : 'low';
  });
  protected readonly failure = computed(() => {
    const run = this.run();
    if (run?.status !== 'FAILED') {
      return null;
    }
    return RUN_ERROR_LABELS[run.errorCode ?? ''] ?? 'The run failed. Please try again.';
  });
  protected readonly writerReason = computed(() => {
    const writer = this.run()?.steps.find((step) => step.agent === 'WRITER' && step.source === 'SKIPPED');
    return writer?.reason ? REASON_LABELS[writer.reason] : 'the AI writer was unavailable';
  });

  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  ngOnInit(): void {
    this.api.latest(this.applicationId()).subscribe({
      next: (run) => {
        this.show(run);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.loading.set(false);
      },
    });
  }

  protected start(): void {
    if (this.starting() || this.active()) {
      return;
    }
    this.starting.set(true);
    this.errorMessage.set(null);
    this.copied.set(false);
    this.api.start(this.applicationId()).subscribe({
      next: (run) => {
        this.starting.set(false);
        this.show(run);
      },
      error: (error: unknown) => {
        // 409 (no CV, no analysis) and 429 (daily limit) carry messages written for the user.
        this.errorMessage.set(toErrorMessage(error));
        this.starting.set(false);
      },
    });
  }

  protected async copyLetter(): Promise<void> {
    const letter = this.run()?.coverLetter;
    if (!letter) {
      return;
    }
    try {
      await navigator.clipboard.writeText(letter);
      this.copied.set(true);
    } catch {
      this.errorMessage.set('Could not copy automatically. Select the letter and copy it by hand.');
    }
  }

  protected gaugeDash(score: number): string {
    const visible = (Math.max(0, Math.min(100, score)) / 100) * GAUGE_LENGTH;
    return `${visible} ${GAUGE_LENGTH}`;
  }

  protected seconds(latencyMs: number): string {
    return `${(latencyMs / 1000).toFixed(1)} s`;
  }

  private show(run: AgentRun | null): void {
    this.run.set(run);
    this.stopPolling();
    if (run !== null && isActive(run)) {
      this.pollTimer = setTimeout(() => this.poll(), POLL_INTERVAL_MS);
    }
  }

  private poll(): void {
    this.pollTimer = null;
    this.api.latest(this.applicationId()).subscribe({
      next: (run) => {
        this.errorMessage.set(null);
        this.show(run);
      },
      error: (error: unknown) => {
        // The run keeps working on the server; show the problem and try again later.
        this.errorMessage.set(toErrorMessage(error));
        this.pollTimer = setTimeout(() => this.poll(), POLL_INTERVAL_MS * 2);
      },
    });
  }

  private stopPolling(): void {
    if (this.pollTimer !== null) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }
}
