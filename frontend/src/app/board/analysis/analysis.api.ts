import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { JobAnalysis } from './analysis.models';

/** HTTP client for /api/applications/{id}/analysis. */
@Injectable({ providedIn: 'root' })
export class AnalysisApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/applications`;

  /** The application's analysis, or null when it has none yet (the API answers 404). */
  get(applicationId: string): Observable<JobAnalysis | null> {
    return this.http.get<JobAnalysis>(this.url(applicationId)).pipe(
      catchError((error: unknown) =>
        error instanceof HttpErrorResponse &&
        error.status === 404 &&
        error.error?.code === 'ANALYSIS_NOT_FOUND'
          ? of(null)
          : throwError(() => error),
      ),
    );
  }

  /** Analyzes the posting and stores the result, replacing any earlier analysis. */
  analyze(applicationId: string, postingText: string): Observable<JobAnalysis> {
    return this.http.post<JobAnalysis>(this.url(applicationId), { postingText });
  }

  private url(applicationId: string): string {
    return `${this.baseUrl}/${applicationId}/analysis`;
  }
}
