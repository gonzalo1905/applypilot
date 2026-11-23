import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AnalysisApi } from '../analysis/analysis.api';
import { MatchApi } from '../match/match.api';
import { JobApplication } from '../application.models';
import { BoardStore } from '../board.store';
import { card } from '../testing/application.fixtures';
import { ApplicationDialog } from './application-dialog';

describe('ApplicationDialog', () => {
  const existing = card('acme', 'SAVED', 0, {
    company: 'Acme',
    roleTitle: 'Backend Developer',
    jobUrl: 'https://acme.example/jobs/1',
    location: 'Lima',
    workMode: 'HYBRID',
    notes: 'Referred by Ana',
  });

  let store: {
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    remove: ReturnType<typeof vi.fn>;
    move: ReturnType<typeof vi.fn>;
  };
  let fixture: ComponentFixture<ApplicationDialog>;
  let page: HTMLElement;
  let closed: number;

  async function render(application: JobApplication | null, initialStatus = 'SAVED'): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ApplicationDialog],
      providers: [
        { provide: BoardStore, useValue: store },
        // The embedded analysis panel loads on open; these tests are about the form.
        { provide: AnalysisApi, useValue: { get: vi.fn(() => of(null)), analyze: vi.fn() } },
        { provide: MatchApi, useValue: { latest: vi.fn(() => of(null)), start: vi.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ApplicationDialog);
    fixture.componentRef.setInput('application', application);
    fixture.componentRef.setInput('initialStatus', initialStatus);
    closed = 0;
    fixture.componentInstance.closed.subscribe(() => (closed += 1));
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  function type(selector: string, value: string): void {
    const input = page.querySelector<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  async function submit(): Promise<void> {
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function button(label: string): HTMLButtonElement {
    const match = Array.from(page.querySelectorAll('button')).find(
      (candidate) => candidate.textContent?.trim() === label,
    );
    if (match === undefined) {
      throw new Error(`No button labelled "${label}"`);
    }
    return match;
  }

  beforeEach(() => {
    store = {
      create: vi.fn(() => of(existing)),
      update: vi.fn(() => of(existing)),
      remove: vi.fn(() => of(undefined)),
      move: vi.fn(),
    };
  });

  describe('creating', () => {
    it('preselects the column it was opened from', async () => {
      await render(null, 'INTERVIEW');

      expect(page.querySelector('h2')?.textContent).toBe('Add application');
      expect(page.querySelector<HTMLSelectElement>('#status')?.value).toBe('INTERVIEW');
    });

    it('does not save and shows errors when required fields are empty', async () => {
      await render(null);

      await submit();

      expect(store.create).not.toHaveBeenCalled();
      const errors = Array.from(page.querySelectorAll('.field-error')).map((e) => e.textContent?.trim());
      expect(errors).toEqual([
        'Enter the company (up to 120 characters).',
        'Enter the role (up to 160 characters).',
      ]);
      expect(closed).toBe(0);
    });

    it('rejects a job URL that is not http or https', async () => {
      await render(null);
      type('#company', 'Acme');
      type('#roleTitle', 'Developer');
      type('#jobUrl', 'javascript:alert(1)');

      await submit();

      expect(store.create).not.toHaveBeenCalled();
      expect(page.querySelector('.field-error')?.textContent).toContain('http:// or https://');
    });

    it('sends trimmed values with empty optional fields as null, then closes', async () => {
      await render(null, 'APPLIED');
      type('#company', '  Acme  ');
      type('#roleTitle', 'Backend Developer');
      type('#location', '   ');
      type('#workMode', 'REMOTE');
      type('#appliedAt', '2026-11-02');

      await submit();

      expect(store.create).toHaveBeenCalledWith({
        company: 'Acme',
        roleTitle: 'Backend Developer',
        status: 'APPLIED',
        jobUrl: null,
        location: null,
        workMode: 'REMOTE',
        salaryNote: null,
        appliedAt: '2026-11-02',
        notes: null,
      });
      expect(closed).toBe(1);
    });

    it('stays open and shows server errors when saving fails', async () => {
      store.create.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 400,
              error: {
                code: 'VALIDATION_FAILED',
                message: 'Request validation failed',
                fieldErrors: [{ field: 'company', message: 'must not be blank' }],
              },
            }),
        ),
      );
      await render(null);
      type('#company', 'Acme');
      type('#roleTitle', 'Developer');

      await submit();

      expect(page.querySelector('[role="alert"]')?.textContent).toBe('Request validation failed');
      expect(page.querySelector('.field-error')?.textContent).toBe('Company must not be blank.');
      expect(closed).toBe(0);
      expect(button('Add application').disabled).toBe(false);
    });
  });

  describe('editing', () => {
    it('fills the form with the card', async () => {
      await render(existing);

      expect(page.querySelector('h2')?.textContent).toBe('Edit application');
      expect(page.querySelector<HTMLInputElement>('#company')?.value).toBe('Acme');
      expect(page.querySelector<HTMLInputElement>('#jobUrl')?.value).toBe('https://acme.example/jobs/1');
      expect(page.querySelector<HTMLSelectElement>('#workMode')?.value).toBe('HYBRID');
      expect(page.querySelector<HTMLTextAreaElement>('#notes')?.value).toBe('Referred by Ana');
    });

    it('updates the details without moving the card when the status is unchanged', async () => {
      await render(existing);
      type('#company', 'Acme Corp');

      await submit();

      expect(store.update).toHaveBeenCalledWith(
        'acme',
        expect.objectContaining({ company: 'Acme Corp', roleTitle: 'Backend Developer', location: 'Lima' }),
      );
      expect(store.update.mock.calls[0][1]).not.toHaveProperty('status');
      expect(store.move).not.toHaveBeenCalled();
      expect(closed).toBe(1);
    });

    it('moves the card to the end of the new column when the status changes', async () => {
      await render(existing);
      type('#status', 'OFFER');

      await submit();

      expect(store.update).toHaveBeenCalled();
      expect(store.move).toHaveBeenCalledWith('acme', 'OFFER', Number.MAX_SAFE_INTEGER);
    });

    it('asks for confirmation before deleting', async () => {
      await render(existing);

      button('Delete').click();
      await fixture.whenStable();

      expect(store.remove).not.toHaveBeenCalled();
      expect(page.querySelector('.confirm-text')?.textContent).toContain('cannot be undone');

      button('Keep it').click();
      await fixture.whenStable();

      expect(page.querySelector('.confirm-text')).toBeNull();
      expect(store.remove).not.toHaveBeenCalled();
      expect(closed).toBe(0);
    });

    it('deletes the card after confirmation and closes', async () => {
      await render(existing);

      button('Delete').click();
      await fixture.whenStable();
      button('Delete').click();
      await fixture.whenStable();

      expect(store.remove).toHaveBeenCalledWith('acme');
      expect(closed).toBe(1);
    });
  });

  it('has no delete button when creating', async () => {
    await render(null);

    expect(() => button('Delete')).toThrow();
  });

  it('closes without saving when cancelled', async () => {
    await render(null);
    type('#company', 'Acme');

    button('Cancel').click();
    await fixture.whenStable();

    expect(store.create).not.toHaveBeenCalled();
    expect(closed).toBe(1);
  });
});
