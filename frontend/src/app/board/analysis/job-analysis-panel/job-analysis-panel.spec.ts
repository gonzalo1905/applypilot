import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { AnalysisApi } from '../analysis.api';
import { JobAnalysis } from '../analysis.models';
import { JobAnalysisPanel } from './job-analysis-panel';

describe('JobAnalysisPanel', () => {
  const POSTING = 'Senior Java developer at Acme. Spring Boot and PostgreSQL required.';

  function analysis(overrides: Partial<JobAnalysis> = {}): JobAnalysis {
    return {
      id: 'analysis-1',
      applicationId: 'app-1',
      postingText: POSTING,
      company: 'Acme',
      roleTitle: 'Java Developer',
      seniority: 'SENIOR',
      requiredSkills: ['Java', 'Spring Boot', 'PostgreSQL'],
      niceToHaveSkills: ['Kubernetes'],
      workMode: 'REMOTE',
      location: 'Lima, Peru',
      languages: ['English'],
      salary: null,
      summary: 'A backend role building REST APIs.',
      source: 'LLM',
      fallbackReason: null,
      model: 'openai/gpt-oss-120b',
      cached: false,
      analyzedAt: '2026-11-20T10:00:00Z',
      modelCalls: 1,
      promptTokens: 900,
      completionTokens: 150,
      ...overrides,
    };
  }

  let api: { get: ReturnType<typeof vi.fn>; analyze: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<JobAnalysisPanel>;
  let page: HTMLElement;

  async function render(existing: Observable<JobAnalysis | null>): Promise<void> {
    api.get.mockReturnValue(existing);
    await TestBed.configureTestingModule({
      imports: [JobAnalysisPanel],
      providers: [{ provide: AnalysisApi, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(JobAnalysisPanel);
    fixture.componentRef.setInput('applicationId', 'app-1');
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  function chips(label: string): string[] {
    const list = page.querySelector(`ul[aria-label="${label}"]`);
    return Array.from(list?.querySelectorAll('li') ?? []).map((chip) => chip.textContent?.trim() ?? '');
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

  async function paste(text: string): Promise<void> {
    const textarea = page.querySelector<HTMLTextAreaElement>('#postingText')!;
    textarea.value = text;
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  beforeEach(() => {
    api = { get: vi.fn(), analyze: vi.fn() };
  });

  it('shows the stored analysis with skills as chips', async () => {
    await render(of(analysis()));

    expect(api.get).toHaveBeenCalledWith('app-1');
    expect(chips('Required skills')).toEqual(['Java', 'Spring Boot', 'PostgreSQL']);
    expect(chips('Nice-to-have skills')).toEqual(['Kubernetes']);
    expect(page.textContent).toContain('Senior');
    expect(page.textContent).toContain('Remote');
    expect(page.querySelector('.badge-ai')?.textContent?.trim()).toBe('AI');
    expect(page.querySelector('#postingText')).toBeNull();
  });

  it('asks for the posting when there is no analysis and analyzes it', async () => {
    await render(of(null));
    api.analyze.mockReturnValue(of(analysis()));

    expect(button('Analyze job posting').disabled).toBe(true);
    await paste('Too short');
    expect(button('Analyze job posting').disabled).toBe(true);

    await paste(POSTING);
    button('Analyze job posting').click();
    await fixture.whenStable();

    expect(api.analyze).toHaveBeenCalledWith('app-1', POSTING);
    expect(chips('Required skills')).toEqual(['Java', 'Spring Boot', 'PostgreSQL']);
    expect(page.querySelector('#postingText')).toBeNull();
  });

  it('marks a keyword result as FALLBACK and explains why', async () => {
    await render(
      of(
        analysis({
          source: 'FALLBACK',
          fallbackReason: 'NOT_CONFIGURED',
          model: null,
          company: null,
          niceToHaveSkills: [],
        }),
      ),
    );

    expect(page.querySelector('.badge-fallback')?.textContent?.trim()).toBe('FALLBACK');
    expect(page.querySelector('.badge-ai')).toBeNull();
    expect(page.querySelector('.fallback-note')?.textContent).toContain('AI is not configured on this server');
    expect(page.querySelector('ul[aria-label="Nice-to-have skills"]')).toBeNull();
  });

  it('labels a reused result as cached', async () => {
    await render(of(analysis({ cached: true })));

    expect(Array.from(page.querySelectorAll('.badge')).map((badge) => badge.textContent?.trim())).toContain(
      'Cached',
    );
  });

  it('shows the daily limit message and keeps the pasted text', async () => {
    await render(of(null));
    api.analyze.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            error: {
              code: 'AI_DAILY_LIMIT_REACHED',
              message: 'You have used your 20 AI analyses for today. Try again tomorrow.',
            },
          }),
      ),
    );

    await paste(POSTING);
    button('Analyze job posting').click();
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('20 AI analyses for today');
    expect(page.querySelector<HTMLTextAreaElement>('#postingText')?.value).toBe(POSTING);
    expect(button('Analyze job posting').disabled).toBe(false);
  });

  it('refuses text over 20,000 characters instead of cutting it', async () => {
    await render(of(null));

    await paste('x'.repeat(20_001));

    expect(button('Analyze job posting').disabled).toBe(true);
    expect(page.querySelector('.field-error')?.textContent).toContain('too long');
    expect(page.querySelector('.counter')?.textContent).toContain('20,001 / 20,000');
  });

  it('analyzes again starting from the stored text and can cancel', async () => {
    await render(of(analysis()));

    button('Analyze again').click();
    await fixture.whenStable();
    expect(page.querySelector<HTMLTextAreaElement>('#postingText')?.value).toBe(POSTING);

    button('Cancel').click();
    await fixture.whenStable();
    expect(page.querySelector('#postingText')).toBeNull();
    expect(chips('Required skills')).toHaveLength(3);
  });
});
