import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AgentRun } from './match.models';

/** HTTP client for /api/applications/{id}/agent-runs. */
@Injectable({ providedIn: 'root' })
export class MatchApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/applications`;

  /** The application's latest run, or null when it has none yet (the API answers 404). */
  latest(applicationId: string): Observable<AgentRun | null> {
    return this.http
      .get<AgentRun>(`${this.url(applicationId)}/latest`)
      .pipe(
        catchError((error: unknown) =>
          error instanceof HttpErrorResponse &&
          error.status === 404 &&
          error.error?.code === 'AGENT_RUN_NOT_FOUND'
            ? of(null)
            : throwError(() => error),
        ),
      );
  }

  /** Queues a run that reuses the stored analysis; answers right away (202). */
  start(applicationId: string): Observable<AgentRun> {
    return this.http.post<AgentRun>(this.url(applicationId), null);
  }

  private url(applicationId: string): string {
    return `${this.baseUrl}/${applicationId}/agent-runs`;
  }
}
