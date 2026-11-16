import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { User } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { ApplicationStatus, JobApplication } from './application.models';
import { ApplicationsApi } from './applications.api';
import { Board } from './board';
import { card } from './testing/application.fixtures';

const user: User = {
  id: '0b0e7c1e-0000-4000-8000-000000000001',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  createdAt: '2026-01-01T00:00:00Z',
};

describe('Board', () => {
  const acme = card('acme', 'SAVED', 0, {
    company: 'Acme',
    roleTitle: 'Backend Developer',
    location: 'Lima',
    workMode: 'HYBRID',
  });
  const globex = card('globex', 'SAVED', 1, { company: 'Globex' });
  const initech = card('initech', 'APPLIED', 0, { company: 'Initech', appliedAt: '2026-11-02' });

  let auth: { user: ReturnType<typeof signal<User | null>>; refreshUser: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  let api: { list: ReturnType<typeof vi.fn>; move: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<Board>;
  let page: HTMLElement;

  async function render(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Board],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ApplicationsApi, useValue: api },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Board);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  function columnCards(status: ApplicationStatus): string[] {
    const column = page.querySelector(`.column[data-status="${status}"]`)!;
    return Array.from(column.querySelectorAll('.card h3')).map((heading) => heading.textContent ?? '');
  }

  beforeEach(() => {
    auth = { user: signal<User | null>(user), refreshUser: vi.fn(() => of(user)), logout: vi.fn() };
    api = { list: vi.fn(() => of([globex, initech, acme])), move: vi.fn(() => of(acme)) };
  });

  it('shows the five columns in pipeline order with their cards', async () => {
    await render();

    const titles = Array.from(page.querySelectorAll('.column h2')).map((h) => h.textContent);
    expect(titles).toEqual(['Saved', 'Applied', 'Interview', 'Offer', 'Rejected']);
    expect(columnCards('SAVED')).toEqual(['Acme', 'Globex']);
    expect(columnCards('APPLIED')).toEqual(['Initech']);
    expect(page.querySelector('.column[data-status="SAVED"] .count')?.textContent).toBe('2');
  });

  it('shows company, role, location, work mode and date on a card', async () => {
    await render();

    const text = page.querySelector('.column[data-status="SAVED"] .card')?.textContent ?? '';
    expect(text).toContain('Acme');
    expect(text).toContain('Backend Developer');
    expect(text).toContain('Lima');
    expect(text).toContain('Hybrid');
    expect(page.querySelector('.column[data-status="APPLIED"] .card')?.textContent).toContain(
      'Applied Nov 2',
    );
  });

  it('shows an empty state only in columns without cards', async () => {
    await render();

    expect(page.querySelector('.column[data-status="SAVED"] .column-empty')).toBeNull();
    expect(page.querySelector('.column[data-status="OFFER"] .column-empty')?.textContent).toContain(
      'No applications yet',
    );
  });

  it("shows the logged-in user's name and logs out from the header", async () => {
    await render();

    expect(page.querySelector('[data-testid="user-name"]')?.textContent).toBe('Ada Lovelace');
    expect(auth.refreshUser).toHaveBeenCalled();

    page.querySelector<HTMLButtonElement>('app-topbar button')?.click();

    expect(auth.logout).toHaveBeenCalledTimes(1);
  });

  it('offers a retry when the board cannot be loaded', async () => {
    api.list.mockReturnValueOnce(throwError(() => new Error('offline')));
    await render();

    expect(page.querySelector('.board')).toBeNull();
    expect(page.querySelector('.board-status')?.textContent).toContain('Something went wrong');

    page.querySelector<HTMLButtonElement>('.board-status button')?.click();
    await fixture.whenStable();

    expect(columnCards('SAVED')).toEqual(['Acme', 'Globex']);
  });

  it('moves a card to the column and index where it is dropped', async () => {
    await render();
    const saved = { data: 'SAVED' };
    const applied = { data: 'APPLIED' };
    const dropEvent = {
      previousContainer: saved,
      container: applied,
      previousIndex: 0,
      currentIndex: 1,
      item: { data: acme },
    } as unknown as CdkDragDrop<ApplicationStatus, ApplicationStatus, JobApplication>;

    (fixture.componentInstance as unknown as { drop: (event: typeof dropEvent) => void }).drop(dropEvent);
    await fixture.whenStable();

    expect(api.move).toHaveBeenCalledWith('acme', { status: 'APPLIED', position: 1 });
  });

  it('ignores a card dropped back where it was', async () => {
    await render();
    const saved = { data: 'SAVED' };
    const dropEvent = {
      previousContainer: saved,
      container: saved,
      previousIndex: 1,
      currentIndex: 1,
      item: { data: globex },
    } as unknown as CdkDragDrop<ApplicationStatus, ApplicationStatus, JobApplication>;

    (fixture.componentInstance as unknown as { drop: (event: typeof dropEvent) => void }).drop(dropEvent);

    expect(api.move).not.toHaveBeenCalled();
  });

  it('opens the dialog to add a card and to edit an existing one', async () => {
    await render();
    expect(page.querySelector('app-application-dialog')).toBeNull();

    page.querySelector<HTMLButtonElement>('.board-heading button')?.click();
    await fixture.whenStable();
    expect(page.querySelector('app-application-dialog h2')?.textContent).toBe('Add application');

    page.querySelector<HTMLButtonElement>('app-application-dialog button[aria-label="Close"]')?.click();
    await fixture.whenStable();
    expect(page.querySelector('app-application-dialog')).toBeNull();

    page.querySelector<HTMLElement>('.column[data-status="SAVED"] .card')?.click();
    await fixture.whenStable();
    expect(page.querySelector('app-application-dialog h2')?.textContent).toBe('Edit application');
    expect(page.querySelector<HTMLInputElement>('app-application-dialog #company')?.value).toBe('Acme');
  });
});
