import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toErrorMessage } from '../../core/api-error';
import { AccessToken, TokensApi } from './tokens.api';

/**
 * Personal access tokens for the MCP server: create one for Claude Desktop or Claude Code,
 * see it exactly once, and revoke it when no longer needed.
 */
@Component({
  selector: 'app-tokens-panel',
  imports: [DatePipe],
  templateUrl: './tokens-panel.html',
  styleUrl: './tokens-panel.scss',
})
export class TokensPanel implements OnInit {
  private readonly api = inject(TokensApi);

  protected readonly tokens = signal<AccessToken[]>([]);
  protected readonly name = signal('');
  protected readonly newToken = signal<string | null>(null);
  protected readonly copied = signal(false);
  protected readonly busy = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly mcpUrl = this.api.mcpUrl();

  protected readonly active = computed(() =>
    this.tokens().filter((token) => token.revokedAt === null),
  );
  protected readonly canCreate = computed(
    () => !this.busy() && this.name().trim().length > 0 && this.name().length <= 60,
  );

  ngOnInit(): void {
    this.reload();
  }

  protected onName(event: Event): void {
    this.name.set((event.target as HTMLInputElement).value);
  }

  protected create(): void {
    if (!this.canCreate()) {
      return;
    }
    this.busy.set(true);
    this.errorMessage.set(null);
    this.copied.set(false);
    this.api.create(this.name().trim()).subscribe({
      next: (created) => {
        this.newToken.set(created.token);
        this.name.set('');
        this.busy.set(false);
        this.reload();
      },
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.busy.set(false);
      },
    });
  }

  protected revoke(token: AccessToken): void {
    this.busy.set(true);
    this.errorMessage.set(null);
    this.api.revoke(token.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.reload();
      },
      error: (error: unknown) => {
        this.errorMessage.set(toErrorMessage(error));
        this.busy.set(false);
      },
    });
  }

  protected async copy(): Promise<void> {
    const token = this.newToken();
    if (token === null) {
      return;
    }
    try {
      await navigator.clipboard.writeText(token);
      this.copied.set(true);
    } catch {
      this.errorMessage.set('Could not copy automatically. Select the token and copy it by hand.');
    }
  }

  protected dismissToken(): void {
    this.newToken.set(null);
    this.copied.set(false);
  }

  private reload(): void {
    this.api.list().subscribe({
      next: (tokens) => this.tokens.set(tokens),
      error: (error: unknown) => this.errorMessage.set(toErrorMessage(error)),
    });
  }
}
