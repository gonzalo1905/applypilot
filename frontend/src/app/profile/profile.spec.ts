import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { User } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { CvApi, CvMetadata } from './cv.api';
import { MAX_CV_SIZE_BYTES, Profile } from './profile';

const user: User = {
  id: '0b0e7c1e-0000-4000-8000-000000000001',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  createdAt: '2026-01-01T00:00:00Z',
};

const cv: CvMetadata = {
  id: '0b0e7c1e-0000-4000-8000-000000000002',
  fileName: 'ada-cv.pdf',
  sizeBytes: 150_000,
  uploadedAt: '2026-11-16T12:00:00Z',
  textExtracted: true,
  extractedCharacters: 2400,
};

describe('Profile', () => {
  let cvApi: {
    get: ReturnType<typeof vi.fn>;
    upload: ReturnType<typeof vi.fn>;
    download: ReturnType<typeof vi.fn>;
    remove: ReturnType<typeof vi.fn>;
  };
  let fixture: ComponentFixture<Profile>;
  let page: HTMLElement;

  async function render(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user: signal<User | null>(user), logout: vi.fn() } },
        { provide: CvApi, useValue: cvApi },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(Profile);
    page = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  async function chooseFile(file: File): Promise<void> {
    const input = page.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
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

  function pdf(name = 'cv.pdf', size = 1000): File {
    return new File([new Uint8Array(size)], name, { type: 'application/pdf' });
  }

  beforeEach(() => {
    cvApi = {
      get: vi.fn(() => of(null)),
      upload: vi.fn(() => of(cv)),
      download: vi.fn(() => of(new Blob(['pdf']))),
      remove: vi.fn(() => of(undefined)),
    };
  });

  it('shows the account details', async () => {
    await render();

    const details = page.querySelector('.details')?.textContent ?? '';
    expect(details).toContain('Ada Lovelace');
    expect(details).toContain('ada@example.com');
  });

  it('invites the user to upload when there is no CV', async () => {
    await render();

    expect(page.textContent).toContain('You have not uploaded a CV yet.');
    expect(page.querySelector('label.btn')?.textContent).toContain('Upload CV');
    expect(() => button('Download')).toThrow();
  });

  it('shows the stored CV with its size', async () => {
    cvApi.get.mockReturnValue(of(cv));
    await render();

    expect(page.querySelector('.file-name')?.textContent).toBe('ada-cv.pdf');
    expect(page.querySelector('.file-info')?.textContent).toContain('146 KB');
    expect(page.querySelector('label.btn')?.textContent).toContain('Replace CV');
    expect(page.querySelector('.alert-warning')).toBeNull();
  });

  it('warns when no text could be read from the PDF', async () => {
    cvApi.get.mockReturnValue(of({ ...cv, textExtracted: false, extractedCharacters: 0 }));
    await render();

    expect(page.querySelector('.alert-warning')?.textContent).toContain('No text could be read');
  });

  it('uploads a chosen PDF and shows it', async () => {
    await render();
    const file = pdf('ada-cv.pdf');

    await chooseFile(file);

    expect(cvApi.upload).toHaveBeenCalledWith(file);
    expect(page.querySelector('.file-name')?.textContent).toBe('ada-cv.pdf');
  });

  it('rejects a file that is not a PDF without calling the API', async () => {
    await render();

    await chooseFile(new File(['hello'], 'notes.txt', { type: 'text/plain' }));

    expect(cvApi.upload).not.toHaveBeenCalled();
    expect(page.querySelector('[role="alert"]')?.textContent).toBe('Only PDF files are accepted.');
  });

  it('rejects a PDF larger than the limit without calling the API', async () => {
    await render();

    await chooseFile(pdf('huge.pdf', MAX_CV_SIZE_BYTES + 1));

    expect(cvApi.upload).not.toHaveBeenCalled();
    expect(page.querySelector('[role="alert"]')?.textContent).toBe('The file is larger than 5 MB.');
  });

  it('shows the server message when the upload is rejected', async () => {
    cvApi.upload.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'INVALID_REQUEST', message: 'The file is not a readable PDF', fieldErrors: [] },
          }),
      ),
    );
    await render();

    await chooseFile(pdf());

    expect(page.querySelector('[role="alert"]')?.textContent).toBe('The file is not a readable PDF');
    expect(page.querySelector('.file-name')).toBeNull();
  });

  it('deletes the CV only after confirmation', async () => {
    cvApi.get.mockReturnValue(of(cv));
    await render();

    button('Delete').click();
    await fixture.whenStable();
    expect(cvApi.remove).not.toHaveBeenCalled();
    expect(page.querySelector('.confirm-text')?.textContent).toContain('cannot be undone');

    button('Delete').click();
    await fixture.whenStable();

    expect(cvApi.remove).toHaveBeenCalledTimes(1);
    expect(page.textContent).toContain('You have not uploaded a CV yet.');
  });

  it('keeps the CV when the deletion is cancelled', async () => {
    cvApi.get.mockReturnValue(of(cv));
    await render();

    button('Delete').click();
    await fixture.whenStable();
    button('Keep it').click();
    await fixture.whenStable();

    expect(cvApi.remove).not.toHaveBeenCalled();
    expect(page.querySelector('.file-name')?.textContent).toBe('ada-cv.pdf');
  });
});
