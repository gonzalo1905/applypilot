import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { environment } from '../../environments/environment';

/** CV metadata returned by the API (the file itself is downloaded separately). */
export interface CvMetadata {
  id: string;
  fileName: string;
  sizeBytes: number;
  uploadedAt: string;
  /** False for PDFs without a text layer, such as scans. */
  textExtracted: boolean;
  extractedCharacters: number;
}

@Injectable({ providedIn: 'root' })
export class CvApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/users/me/cv`;

  /** The user's CV, or null when none has been uploaded yet (the API answers 404). */
  get(): Observable<CvMetadata | null> {
    return this.http.get<CvMetadata>(this.baseUrl).pipe(
      catchError((error: unknown) =>
        error instanceof HttpErrorResponse && error.status === 404 ? of(null) : throwError(() => error),
      ),
    );
  }

  /** Uploads a CV, replacing the previous one. */
  upload(file: File): Observable<CvMetadata> {
    const body = new FormData();
    body.append('file', file, file.name);
    // No Content-Type header here: the browser sets multipart/form-data with its boundary.
    return this.http.post<CvMetadata>(this.baseUrl, body);
  }

  /** The PDF bytes. Fetched through HttpClient so the interceptor adds the token. */
  download(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/file`, { responseType: 'blob' });
  }

  remove(): Observable<void> {
    return this.http.delete<void>(this.baseUrl);
  }
}
