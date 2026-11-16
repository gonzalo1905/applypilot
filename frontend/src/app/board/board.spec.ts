import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { User } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { Board } from './board';

const user: User = {
  id: '0b0e7c1e-0000-4000-8000-000000000001',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  createdAt: '2026-01-01T00:00:00Z',
};

describe('Board', () => {
  const auth = {
    user: signal<User | null>(user),
    refreshUser: vi.fn(() => of(user)),
    logout: vi.fn(),
  };

  async function render(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [Board],
      providers: [{ provide: AuthService, useValue: auth }],
    }).compileComponents();
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the five empty columns in pipeline order', async () => {
    const page = await render();

    const titles = Array.from(page.querySelectorAll('.column h2')).map((h) => h.textContent);
    expect(titles).toEqual(['Saved', 'Applied', 'Interview', 'Offer', 'Rejected']);
    expect(page.querySelectorAll('.column-empty')).toHaveLength(5);
  });

  it("shows the logged-in user's name and refreshes the profile", async () => {
    const page = await render();

    expect(page.querySelector('[data-testid="user-name"]')?.textContent).toBe('Ada Lovelace');
    expect(page.querySelector('.avatar')?.textContent).toBe('AL');
    expect(auth.refreshUser).toHaveBeenCalled();
  });

  it('logs out when the button is clicked', async () => {
    const page = await render();

    page.querySelector<HTMLButtonElement>('.topbar button')?.click();

    expect(auth.logout).toHaveBeenCalledTimes(1);
  });
});
