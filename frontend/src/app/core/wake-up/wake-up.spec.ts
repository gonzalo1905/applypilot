import { TestBed } from '@angular/core/testing';
import { SLOW_REQUEST_MS, WakeUpBanner, WakeUpNotice } from './wake-up';

describe('WakeUpNotice', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('shows only while a request is slower than the threshold', () => {
    const notice = new WakeUpNotice();

    const fast = notice.track();
    vi.advanceTimersByTime(SLOW_REQUEST_MS - 1);
    fast();
    expect(notice.visible()).toBe(false);

    const slow = notice.track();
    const alsoSlow = notice.track();
    vi.advanceTimersByTime(SLOW_REQUEST_MS);
    expect(notice.visible()).toBe(true);
    slow();
    expect(notice.visible()).toBe(true);
    alsoSlow();
    expect(notice.visible()).toBe(false);
  });

  it('renders the banner while visible', async () => {
    await TestBed.configureTestingModule({ imports: [WakeUpBanner] }).compileComponents();
    const fixture = TestBed.createComponent(WakeUpBanner);
    const notice = TestBed.inject(WakeUpNotice);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();

    const done = notice.track();
    vi.advanceTimersByTime(SLOW_REQUEST_MS);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain(
      'waking up',
    );

    done();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
  });
});
