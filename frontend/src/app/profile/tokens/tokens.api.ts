import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** A personal access token as listed: only its prefix, never the secret. */
export interface AccessToken {
  id: string;
  name: string;
  prefix: string;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

/** The answer to creating a token: the only time the secret is returned. */
export interface CreatedToken {
  details: AccessToken;
  token: string;
}

/** HTTP client for /api/users/me/tokens (personal access tokens for the MCP server). */
@Injectable({ providedIn: 'root' })
export class TokensApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/users/me/tokens`;

  list(): Observable<AccessToken[]> {
    return this.http.get<AccessToken[]>(this.baseUrl);
  }

  create(name: string): Observable<CreatedToken> {
    return this.http.post<CreatedToken>(this.baseUrl, { name });
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Where MCP clients connect: the API's origin plus /mcp. */
  mcpUrl(): string {
    const api = new URL(environment.apiUrl, window.location.origin);
    return `${api.origin}/mcp`;
  }
}
