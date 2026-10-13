import { HttpErrorResponse } from '@angular/common/http';

/** The RFC 9457 body every failing endpoint returns (`backend/.../web/ApiExceptionHandler`). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Present on validation failures: field name to message. */
  fieldErrors?: Record<string, string>;
  /** Present on unexpected failures, and in the matching server log line. */
  correlationId?: string;
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null && ('title' in value || 'detail' in value);
}

/**
 * A message a user can act on: the server's own wording when it sent a problem detail, and something honest
 * otherwise — the status code alone, never a fabricated cause.
 */
export function problemMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The backend did not answer. Is it running on the address the dev proxy targets?';
    }
    const body: unknown = error.error;
    if (isProblemDetail(body)) {
      const fieldErrors = body.fieldErrors
        ? Object.entries(body.fieldErrors)
            .map(([field, message]) => `${field}: ${message}`)
            .join('; ')
        : '';
      const parts = [body.title, body.detail, fieldErrors].filter((part): part is string => !!part);
      const correlation = body.correlationId ? ` (correlationId ${body.correlationId})` : '';
      if (parts.length > 0) {
        return parts.join(' — ') + correlation;
      }
    }
    return `The backend answered ${error.status} ${error.statusText}.`;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return 'The request failed for an unknown reason.';
}
