import {
  Component,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { toErrorMessage, toFieldErrors } from '../../core/api-error';
import {
  ApplicationDetails,
  ApplicationStatus,
  BOARD_COLUMNS,
  JobApplication,
  WORK_MODE_LABELS,
  WorkMode,
} from '../application.models';
import { BoardStore } from '../board.store';

/**
 * Modal form to create a card or edit an existing one (including deleting it, after a
 * confirmation step). Built on the browser's native <dialog>, which provides the backdrop,
 * focus trapping and Esc-to-close without any library.
 */
@Component({
  selector: 'app-application-dialog',
  imports: [ReactiveFormsModule],
  templateUrl: './application-dialog.html',
  styleUrl: './application-dialog.scss',
})
export class ApplicationDialog {
  private readonly store = inject(BoardStore);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly dialogElement = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  /** The card to edit, or null to create a new one. */
  readonly application = input<JobApplication | null>(null);
  /** Column preselected when creating a card. */
  readonly initialStatus = input<ApplicationStatus>('SAVED');
  readonly closed = output<void>();

  protected readonly columns = BOARD_COLUMNS;
  protected readonly workModes = Object.entries(WORK_MODE_LABELS) as [WorkMode, string][];
  protected readonly isEditing = computed(() => this.application() !== null);
  protected readonly saving = signal(false);
  protected readonly confirmingDelete = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  // These rules mirror the backend's request validation; the backend stays the source of truth.
  protected readonly form = this.formBuilder.group({
    company: ['', [Validators.required, Validators.maxLength(120)]],
    roleTitle: ['', [Validators.required, Validators.maxLength(160)]],
    status: this.formBuilder.control<ApplicationStatus>('SAVED'),
    jobUrl: ['', [Validators.pattern(/^https?:\/\/\S+$/), Validators.maxLength(2048)]],
    location: ['', [Validators.maxLength(120)]],
    workMode: this.formBuilder.control<WorkMode | ''>(''),
    salaryNote: ['', [Validators.maxLength(120)]],
    appliedAt: [''],
    notes: ['', [Validators.maxLength(5000)]],
  });

  constructor() {
    afterNextRender(() => {
      this.fillForm();
      const dialog = this.dialogElement().nativeElement;
      // Guarded because not every environment (older browsers, test DOMs) implements it.
      if (typeof dialog.showModal === 'function') {
        dialog.showModal();
      } else {
        dialog.setAttribute('open', '');
      }
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const existing = this.application();
    const { status, ...details } = this.readForm();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.serverErrors.set({});

    const request =
      existing === null
        ? this.store.create({ ...details, status })
        : this.store.update(existing.id, details);

    request.subscribe({
      next: (saved) => {
        // Status is not part of an update: changing the column is a move, sent to the end.
        if (existing !== null && status !== existing.status) {
          this.store.move(saved.id, status, Number.MAX_SAFE_INTEGER);
        }
        this.close();
      },
      error: (error: unknown) => this.showError(error),
    });
  }

  protected askToDelete(): void {
    this.confirmingDelete.set(true);
  }

  protected cancelDelete(): void {
    this.confirmingDelete.set(false);
  }

  protected confirmDelete(): void {
    const existing = this.application();
    if (existing === null) {
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);
    this.store.remove(existing.id).subscribe({
      next: () => this.close(),
      error: (error: unknown) => {
        this.confirmingDelete.set(false);
        this.showError(error);
      },
    });
  }

  /** Clicking the dimmed area outside the form closes the dialog. */
  protected onBackdropClick(event: MouseEvent): void {
    if (event.target === this.dialogElement().nativeElement) {
      this.close();
    }
  }

  protected close(): void {
    const dialog = this.dialogElement().nativeElement;
    if (dialog.open && typeof dialog.close === 'function') {
      dialog.close(); // fires the native "close" event, which emits `closed`
    } else {
      this.closed.emit();
    }
  }

  protected onNativeClose(): void {
    this.closed.emit();
  }

  private showError(error: unknown): void {
    this.errorMessage.set(toErrorMessage(error));
    this.serverErrors.set(toFieldErrors(error));
    this.saving.set(false);
  }

  private fillForm(): void {
    const existing = this.application();
    if (existing === null) {
      this.form.controls.status.setValue(this.initialStatus());
      return;
    }
    this.form.setValue({
      company: existing.company,
      roleTitle: existing.roleTitle,
      status: existing.status,
      jobUrl: existing.jobUrl ?? '',
      location: existing.location ?? '',
      workMode: existing.workMode ?? '',
      salaryNote: existing.salaryNote ?? '',
      appliedAt: existing.appliedAt ?? '',
      notes: existing.notes ?? '',
    });
  }

  /** Form values as the API expects them: trimmed, with empty optional fields as null. */
  private readForm(): ApplicationDetails & { status: ApplicationStatus } {
    const value = this.form.getRawValue();
    const optional = (text: string): string | null => (text.trim() === '' ? null : text.trim());
    return {
      company: value.company.trim(),
      roleTitle: value.roleTitle.trim(),
      status: value.status,
      jobUrl: optional(value.jobUrl),
      location: optional(value.location),
      workMode: value.workMode === '' ? null : value.workMode,
      salaryNote: optional(value.salaryNote),
      appliedAt: optional(value.appliedAt),
      notes: optional(value.notes),
    };
  }
}
