import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toErrorMessage } from '../../../core/api-error';
import { WORK_MODE_LABELS } from '../../application.models';
import { AnalysisApi } from '../analysis.api';
import {
  FALLBACK_REASON_LABELS,
  JobAnalysis,
  POSTING_MAX_LENGTH,
  POSTING_MIN_LENGTH,
  SENIORITY_LABELS,
} from '../analysis.models';

/**
 * Paste a job posting, analyze it, and see the extracted skills as chips. Shown inside the
 * application dialog for existing cards. Results from the keyword fallback are labelled as
 * such, so they are never mistaken for an AI result.
 */
@Component({
  selector: 'app-job-analysis-panel',
  templateUrl: './job-analysis-panel.html',
  styleUrl: './job-analysis-panel.scss',
})
export class JobAnalysisPanel implements OnInit {
  private readonly api = inject(AnalysisApi);

  readonly applicationId = input.required<string>();

  protected readonly maxLength = POSTING_MAX_LENGTH;
  protected readonly seniorityLabels = SENIORITY_LABELS;
  protected readonly workModeLabels = WORK_MODE_LABELS;
  protected readonly fallbackLabels = FALLBACK_REASON_LABELS;

  protected readonly analysis = signal<JobAnalysis | null>(null);
  protected readonly loading = signal(true);
  protected readonly analyzing = signal(false);
  protected readonly editing = signal(false);
  protected readonly postingText = signal('');
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly length = computed(() => this.postingText().trim().length);
  protected readonly tooLong = computed(() => this.postingText().length > POSTING_MAX_LENGTH);
  protected readonly canAnalyze = computed(
    () => !this.analyzing() && this.length() >= POSTING_MIN_LENGTH && !this.tooLong(),
  );

  ngOnInit(): void {
    this.api.get(this.applicationId()).subscribe({
      next: (analysis) => {
        this.analysis.set(analysis);
        this.editing.set(analysis === null);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.editing.set(true);
        this.loading.set(false);
      },
    });
  }

  protected onInput(event: Event): void {
    this.postingText.set((event.target as HTMLTextAreaElement).value);
  }

  protected startEditing(): void {
    this.postingText.set(this.analysis()?.postingText ?? '');
    this.errorMessage.set(null);
    this.editing.set(true);
  }

  protected cancelEditing(): void {
    this.errorMessage.set(null);
    this.editing.set(false);
  }

  protected analyze(): void {
    if (!this.canAnalyze()) {
      return;
    }
    this.analyzing.set(true);
    this.errorMessage.set(null);
    this.api.analyze(this.applicationId(), this.postingText()).subscribe({
      next: (analysis) => {
        this.analysis.set(analysis);
        this.editing.set(false);
        this.analyzing.set(false);
      },
      error: (error: unknown) => {
        // The 429 of the daily limit carries a message written for the user.
        this.errorMessage.set(toErrorMessage(error));
        this.analyzing.set(false);
      },
    });
  }
}
