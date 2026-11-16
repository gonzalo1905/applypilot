import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { JobApplication } from './application.models';
import { ApplicationsApi } from './applications.api';
import { BoardStore } from './board.store';
import { card } from './testing/application.fixtures';

describe('BoardStore', () => {
  let api: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    move: ReturnType<typeof vi.fn>;
    remove: ReturnType<typeof vi.fn>;
  };
  let store: BoardStore;

  const a = card('a', 'SAVED', 0);
  const b = card('b', 'SAVED', 1);
  const d = card('d', 'INTERVIEW', 0);

  function column(status: string): string[] {
    return store
      .columns()
      .find((item) => item.status === status)!
      .cards.map((item) => item.id);
  }

  beforeEach(() => {
    api = {
      list: vi.fn(() => of([b, d, a])),
      create: vi.fn(),
      update: vi.fn(),
      move: vi.fn(),
      remove: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [BoardStore, { provide: ApplicationsApi, useValue: api }],
    });
    store = TestBed.inject(BoardStore);
  });

  it('loads the cards and groups them by column in position order', () => {
    store.load();

    expect(store.loading()).toBe(false);
    expect(store.columns().map((item) => item.status)).toEqual([
      'SAVED',
      'APPLIED',
      'INTERVIEW',
      'OFFER',
      'REJECTED',
    ]);
    expect(column('SAVED')).toEqual(['a', 'b']);
    expect(column('INTERVIEW')).toEqual(['d']);
    expect(column('OFFER')).toEqual([]);
    expect(store.total()).toBe(3);
  });

  it('reports a load failure instead of showing an empty board', () => {
    api.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    store.load();

    expect(store.loading()).toBe(false);
    expect(store.loadError()).toContain('Cannot reach the server');
  });

  it('adds a created card to its column', () => {
    store.load();
    const created = card('new', 'SAVED', 2);
    api.create.mockReturnValue(of(created));

    store.create({ ...created }).subscribe();

    expect(column('SAVED')).toEqual(['a', 'b', 'new']);
  });

  it('replaces an updated card', () => {
    store.load();
    api.update.mockReturnValue(of({ ...a, company: 'Renamed' }));

    store.update('a', { ...a }).subscribe();

    const saved = store.columns()[0].cards[0];
    expect(saved.company).toBe('Renamed');
  });

  it('removes a deleted card and closes the gap', () => {
    store.load();
    api.remove.mockReturnValue(of(undefined));

    store.remove('a').subscribe();

    expect(store.columns()[0].cards.map((item) => `${item.id}:${item.position}`)).toEqual(['b:0']);
  });

  it('does not remove the card when the delete request fails', () => {
    store.load();
    api.remove.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    store.remove('a').subscribe({ error: () => undefined });

    expect(column('SAVED')).toEqual(['a', 'b']);
  });

  it('moves a card immediately, before the server answers', () => {
    store.load();
    const pending = new Subject<JobApplication>();
    api.move.mockReturnValue(pending);

    store.move('a', 'INTERVIEW', 0);

    expect(api.move).toHaveBeenCalledWith('a', { status: 'INTERVIEW', position: 0 });
    expect(column('SAVED')).toEqual(['b']);
    expect(column('INTERVIEW')).toEqual(['a', 'd']);
  });

  it('keeps the move and takes server-computed fields when the server confirms', () => {
    store.load();
    api.move.mockReturnValue(of({ ...a, status: 'APPLIED', position: 0, appliedAt: '2026-11-16' }));

    store.move('a', 'APPLIED', 0);

    const moved = store.columns()[1].cards[0];
    expect(moved.id).toBe('a');
    expect(moved.appliedAt).toBe('2026-11-16');
    expect(store.actionError()).toBeNull();
  });

  it('rolls the move back, explains why and re-reads the board when the server rejects it', () => {
    store.load();
    api.list.mockClear();
    api.move.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { code: 'APPLICATION_NOT_FOUND', message: 'Application not found', fieldErrors: [] },
          }),
      ),
    );

    store.move('a', 'INTERVIEW', 0);

    expect(column('SAVED')).toEqual(['a', 'b']);
    expect(column('INTERVIEW')).toEqual(['d']);
    expect(store.actionError()).toBe('The card could not be moved. Application not found');
    expect(api.list).toHaveBeenCalledTimes(1);
  });

  it('sends nothing when a card is dropped where it already was', () => {
    store.load();

    store.move('b', 'SAVED', 1);

    expect(api.move).not.toHaveBeenCalled();
  });
});
