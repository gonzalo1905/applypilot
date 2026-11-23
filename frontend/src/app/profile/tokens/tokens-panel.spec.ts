import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AccessToken, TokensApi } from './tokens.api';
import { TokensPanel } from './tokens-panel';

describe('TokensPanel', () => {
  const ACTIVE: AccessToken = {
    id: 't1',
    name: 'Claude Desktop',
    prefix: 'ap_pat_abcd',
    createdAt: '2026-11-23T10:00:00Z',
    lastUsedAt: null,
    revokedAt: null,
  };
  const REVOKED: AccessToken = {
    ...ACTIVE,
    id: 't2',
    name: 'Old laptop',
    revokedAt: '2026-11-23T11:00:00Z',
  };

  let api: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    revoke: ReturnType<typeof vi.fn>;
    mcpUrl: ReturnType<typeof vi.fn>;
  };
  let fixture: ComponentFixture<TokensPanel>;
  let page: HTMLElement;

  async function render(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TokensPanel],
      providers: [{ provide: TokensApi, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(TokensPanel);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function button(label: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    )!;
  }

  beforeEach(() => {
    api = {
      list: vi.fn(() => of([ACTIVE, REVOKED])),
      create: vi.fn(() => of({ details: ACTIVE, token: 'ap_pat_secret-value' })),
      revoke: vi.fn(() => of(undefined)),
      mcpUrl: vi.fn(() => 'https://api.example.com/mcp'),
    };
  });

  it('lists only active tokens, by prefix, never a secret', async () => {
    await render();

    const items = Array.from(page.querySelectorAll('ul[aria-label="Active tokens"] li'));
    expect(items).toHaveLength(1);
    expect(items[0].textContent).toContain('Claude Desktop');
    expect(items[0].textContent).toContain('ap_pat_abcd');
    expect(page.textContent).not.toContain('Old laptop');
  });

  it('shows a new token once, with the server URL', async () => {
    await render();
    const input = page.querySelector<HTMLInputElement>('#token-name')!;
    input.value = '  My laptop ';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    button('Create token').click();
    fixture.detectChanges();

    expect(api.create).toHaveBeenCalledWith('My laptop');
    expect(page.querySelector('.secret')?.textContent).toBe('ap_pat_secret-value');
    expect(page.textContent).toContain('https://api.example.com/mcp');

    button('Done').click();
    fixture.detectChanges();
    expect(page.querySelector('.secret')).toBeNull();
  });

  it('cannot create a token without a name', async () => {
    await render();

    expect(button('Create token').disabled).toBe(true);
  });

  it('revokes a token', async () => {
    await render();

    button('Revoke').click();

    expect(api.revoke).toHaveBeenCalledWith('t1');
    expect(api.list).toHaveBeenCalledTimes(2);
  });
});
