import { TestBed } from '@angular/core/testing';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  afterEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset['theme'];
    vi.restoreAllMocks();
  });

  function create(): ThemeService {
    TestBed.resetTestingModule();
    return TestBed.inject(ThemeService);
  }

  it('starts dark when nothing is saved', () => {
    const theme = create();

    expect(theme.theme()).toBe('dark');
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });

  it('restores a saved light theme', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'light');

    expect(create().theme()).toBe('light');
  });

  it('toggles and remembers the choice', () => {
    const theme = create();

    theme.toggle();

    expect(theme.theme()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('keeps working when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const theme = create();

    expect(theme.theme()).toBe('dark');
    theme.toggle();
    expect(theme.theme()).toBe('light');
  });
});
