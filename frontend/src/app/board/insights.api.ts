import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Board summary (see the backend's InsightsResponse). */
export interface BoardInsights {
  /** Latest match score (0 to 100) by application id; never-matched applications are absent. */
  matchScores: Record<string, number>;
  aiCallsLeft: number;
  aiDailyLimit: number;
}

@Injectable({ providedIn: 'root' })
export class InsightsApi {
  private readonly http = inject(HttpClient);

  get(): Observable<BoardInsights> {
    return this.http.get<BoardInsights>(`${environment.apiUrl}/insights`);
  }
}
