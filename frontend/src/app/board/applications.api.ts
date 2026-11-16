import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ApplicationDetails,
  CreateApplicationRequest,
  JobApplication,
  MoveApplicationRequest,
} from './application.models';

/** Thin HTTP client for /api/applications. No state: that lives in the BoardStore. */
@Injectable({ providedIn: 'root' })
export class ApplicationsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/applications`;

  list(): Observable<JobApplication[]> {
    return this.http.get<JobApplication[]>(this.baseUrl);
  }

  create(request: CreateApplicationRequest): Observable<JobApplication> {
    return this.http.post<JobApplication>(this.baseUrl, request);
  }

  update(id: string, details: ApplicationDetails): Observable<JobApplication> {
    return this.http.put<JobApplication>(`${this.baseUrl}/${id}`, details);
  }

  move(id: string, request: MoveApplicationRequest): Observable<JobApplication> {
    return this.http.patch<JobApplication>(`${this.baseUrl}/${id}/move`, request);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
