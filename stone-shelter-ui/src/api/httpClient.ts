import { ApiError } from './ApiError';

export async function requestJson<T>(path: string, options?: RequestInit, isValid?: (body: T) => boolean): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${(import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')}${path}`, options);
  } catch {
    throw new ApiError(0, 'The backend could not be reached. Please try again.');
  }
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new ApiError(response.status, response.ok
      ? 'The backend returned an invalid response. Please try again.'
      : `The request failed (HTTP ${response.status}). Please try again.`);
  }
  if (!response.ok) {
    const problem = body as { detail?: unknown; title?: unknown } | null;
    const message = [problem?.detail, problem?.title].find(value => typeof value === 'string' && value.trim());
    throw new ApiError(response.status, typeof message === 'string' ? message : `The request failed (HTTP ${response.status}). Please try again.`);
  }
  if (!body || typeof body !== 'object' || Array.isArray(body) || (isValid && !isValid(body as T))) {
    throw new ApiError(response.status, 'The backend returned an invalid response. Please try again.');
  }
  return body as T;
}

export function jsonRequest(body: unknown): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}
