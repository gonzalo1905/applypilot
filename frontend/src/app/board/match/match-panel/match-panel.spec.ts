import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { MatchApi } from '../match.api';
import { AgentRun, RunStep } from '../match.models';
import { MatchPanel, POLL_INTERVAL_MS } from './match-panel';

describe('MatchPanel', () => {
  const LETTER = 'Dear Acme team,\n\nI have 6 years of Java experience.\n\nKind regards,\nAna';

  function step(overrides: Partial<RunStep>): RunStep {
    return {
      order: 1,
      agent: 'MATCHER',
      attempt: 1,
      source: 'LLM',
      reason: null,
      model: 'openai/gpt-oss-120b',
      modelCalls: 2,
      toolCalls: 3,
      promptTokens: 900,
      completionTokens: 100,
      latencyMs: 2400,
      inputHash: 'a'.repeat(64),
      startedAt: '2026-11-22T10:00:00Z',
      ...overrides,
    };
  }

  function run(overrides: Partial<AgentRun> = {}): AgentRun {
    return {
      id: 'run-1',
      applicationId: 'app-1',
      status: 'SUCCEEDED',
      currentAgent: null,
      errorCode: null,
      match: {
        score: 71,
        matchedSkills: [
          { skill: 'Java', required: true, evidence: '6 years of Java experience' },
          { skill: 'Docker', required: false, evidence: 'Docker on AWS' },
        ],
        missingSkills: [{ skill: 'Kafka', required: true }],
        summary: 'Good fit without Kafka.',
        rejectedEvidence: 0,
      },
      coverLetter: LETTER,
      letterLanguage: 'en',
      review: {
        approved: true,
        fabricatedClaims: [],
        missingRequirements: [],
        toneIssues: [],
        lengthIssue: null,
        suggestions: [],
        wordCount: 230,
        aiChecked: true,
      },
      revisions: 1,
      steps: [
        step({
          order: 1,
          agent: 'EXTRACTOR',
          source: 'REUSED',
          modelCalls: 0,
          toolCalls: 0,
          promptTokens: null,
        }),
        step({ order: 2 }),
        step({ order: 3, agent: 'WRITER', toolCalls: 0 }),
        step({ order: 4, agent: 'REVIEWER', toolCalls: 0 }),
      ],
      createdAt: '2026-11-22T10:00:00Z',
      startedAt: '2026-11-22T10:00:00Z',
      finishedAt: '2026-11-22T10:00:20Z',
      ...overrides,
    };
  }

  let api: { latest: ReturnType<typeof vi.fn>; start: ReturnType<typeof vi.fn> };
  let fixture: ComponentFixture<MatchPanel>;
  let page: HTMLElement;

  async function render(latest: Observable<AgentRun | null>): Promise<void> {
    api.latest.mockReturnValue(latest);
    await TestBed.configureTestingModule({
      imports: [MatchPanel],
      providers: [{ provide: MatchApi, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(MatchPanel);
    fixture.componentRef.setInput('applicationId', 'app-1');
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function text(selector: string): string {
    return page.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
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
    api = { latest: vi.fn(), start: vi.fn() };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('invites the user to start when there is no run yet', async () => {
    await render(of(null));

    expect(api.latest).toHaveBeenCalledWith('app-1');
    expect(page.textContent).toContain('Compare this posting with your CV');
    expect(button('Match with my CV').disabled).toBe(false);
  });

  it('shows the score, the skills with their evidence, the letter, the review and the timeline', async () => {
    await render(of(run()));

    expect(page.querySelector('svg.gauge')?.getAttribute('aria-label')).toBe(
      'Match score 71 out of 100',
    );
    expect(page.querySelector('svg.gauge')?.classList).toContain('gauge-high');
    const matched = Array.from(page.querySelectorAll('ul[aria-label="Matched skills"] li')).map(
      (item) => [
        item.querySelector('.chip')?.textContent?.trim(),
        item.querySelector('q')?.textContent?.trim(),
      ],
    );
    expect(matched).toEqual([
      ['Java', '6 years of Java experience'],
      ['Docker', 'Docker on AWS'],
    ]);
    expect(text('ul[aria-label="Missing skills"]')).toBe('Kafka');
    expect(page.querySelector('pre.letter')?.textContent).toBe(LETTER);
    expect(text('.review')).toContain('Approved');
    expect(text('.review')).toContain('230 words · 1 revision(s)');
    const steps = Array.from(page.querySelectorAll('ol[aria-label="Run timeline"] li')).map(
      (item) =>
        Array.from(item.querySelectorAll('span')).map((part) =>
          part.textContent?.replace(/\s+/g, ' ').trim(),
        ),
    );
    expect(steps).toHaveLength(4);
    expect(steps[0]).toEqual(['Extractor', 'Reused', '2.4 s']);
    expect(steps[1]).toEqual([
      'Matcher',
      'AI',
      '2.4 s · 2 AI call(s) · 3 tool call(s) · 1000 tokens',
    ]);
    expect(button('Run again').disabled).toBe(false);
  });

  it('copies the letter to the clipboard', async () => {
    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    await render(of(run()));

    button('Copy').click();
    await fixture.whenStable();

    expect(writeText).toHaveBeenCalledWith(LETTER);
    expect(button('Copied')).toBeTruthy();
  });

  it('lists what the reviewer found when the letter needs attention', async () => {
    await render(
      of(
        run({
          review: {
            approved: false,
            fabricatedClaims: [{ claim: '10 years at Google', reason: 'Not in the CV' }],
            missingRequirements: ['Mention Spring Boot'],
            toneIssues: ['Too generic'],
            lengthIssue: 'The letter has 120 words; aim for 200 to 350.',
            suggestions: ['Name a project'],
            wordCount: 120,
            aiChecked: false,
          },
        }),
      ),
    );

    const review = text('.review');
    expect(review).toContain('Needs your attention');
    expect(review).toContain('checked by code only');
    expect(review).toContain('10 years at Google: Not in the CV');
    expect(review).toContain('Mention Spring Boot');
    expect(review).toContain('Too generic');
    expect(review).toContain('120 words; aim for 200 to 350');
    expect(review).toContain('Name a project');
  });

  it('explains a partial run without a letter', async () => {
    await render(
      of(
        run({
          status: 'PARTIAL',
          coverLetter: null,
          review: null,
          steps: [step({ order: 3, agent: 'WRITER', source: 'SKIPPED', reason: 'RATE_LIMITED' })],
        }),
      ),
    );

    expect(page.textContent).toContain(
      'no cover letter could be written: the AI provider is busy right now',
    );
    expect(page.querySelector('pre.letter')).toBeNull();
    expect(page.querySelector('svg.gauge')).not.toBeNull();
  });

  it('shows why a run failed', async () => {
    await render(
      of(run({ status: 'FAILED', errorCode: 'INTERRUPTED', match: null, coverLetter: null })),
    );

    expect(text('[role="alert"]')).toContain('The server restarted');
  });

  it('starts a run and polls until it ends', async () => {
    vi.useFakeTimers();
    await render(of(null));
    api.start.mockReturnValue(
      of(run({ status: 'PENDING', match: null, coverLetter: null, review: null, steps: [] })),
    );
    api.latest.mockReturnValueOnce(
      of(run({ status: 'RUNNING', currentAgent: 'WRITER', coverLetter: null, review: null })),
    );
    api.latest.mockReturnValueOnce(of(run()));

    button('Match with my CV').click();
    fixture.detectChanges();
    expect(api.start).toHaveBeenCalledWith('app-1');
    expect(text('[role="status"]')).toBe('Waiting to start…');
    expect(button('Run again').disabled).toBe(true);

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
    fixture.detectChanges();
    expect(text('[role="status"]')).toBe('Writer is working…');
    // The match is shown while the letter is still being written.
    expect(page.querySelector('svg.gauge')).not.toBeNull();

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
    fixture.detectChanges();
    expect(page.querySelector('[role="status"]')).toBeNull();
    expect(page.querySelector('pre.letter')?.textContent).toBe(LETTER);

    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3);
    expect(api.latest).toHaveBeenCalledTimes(3);
  });

  it('stops polling when the panel is destroyed', async () => {
    vi.useFakeTimers();
    await render(of(run({ status: 'RUNNING', currentAgent: 'MATCHER' })));

    fixture.destroy();
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3);

    expect(api.latest).toHaveBeenCalledTimes(1);
  });

  it('shows the API message when a run cannot start', async () => {
    await render(of(null));
    api.start.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              status: 409,
              code: 'CV_REQUIRED',
              message: 'Upload your CV in your profile before matching a job',
            },
          }),
      ),
    );

    button('Match with my CV').click();
    fixture.detectChanges();

    expect(text('[role="alert"]')).toBe('Upload your CV in your profile before matching a job');
    expect(button('Match with my CV').disabled).toBe(false);
  });
});
