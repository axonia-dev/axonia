import {
  getAccessToken,
  notifyOrganizationOffline,
  notifyUnauthorized,
} from "@/auth/token";
import { apiBaseUrl } from "@/lib/env";

// API version negotiated via the X-API-Version request header (see the backend's
// spring.mvc.apiversion config). The version no longer lives in the URL path.
const API_VERSION = "1";

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  /**
   * The server's own `detail` from an RFC 7807 problem response, undefined when it supplied
   * none. Distinct from `message`, which falls back to a generic `Request to ... failed`
   * sentence in that case - a caller that wants to show the provider's own reason, and only the
   * provider's own reason, reads this rather than `message`.
   */
  readonly detail?: string;
  /** The request value a 400 names, from the problem's own `field` property, such as
   *  `values.fromAddress`; undefined when the problem names none. */
  readonly field?: string;

  constructor(status: number, message: string, code?: string, detail?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    if (code !== undefined) {
      this.code = code;
    }
    if (detail !== undefined) {
      this.detail = detail;
    }
  }

  /** The error for a problem body. A factory beside the constructor rather than a fifth parameter
   *  of it: its four positional ones are what every caller and test already passes. */
  static fromProblem(
    status: number,
    message: string,
    problem: Problem,
  ): ApiError {
    const error = new ApiError(status, message, problem.code, problem.detail);
    return problem.field === undefined
      ? error
      : Object.assign(error, { field: problem.field });
  }
}

interface Problem {
  code?: string;
  detail?: string;
  field?: string;
}

async function readProblem(response: Response): Promise<Problem> {
  try {
    const body: unknown = await response.clone().json();
    if (body === null || typeof body !== "object") return {};
    const problem = body as {
      code?: unknown;
      detail?: unknown;
      field?: unknown;
    };
    return {
      ...(typeof problem.code === "string" ? { code: problem.code } : {}),
      ...(typeof problem.detail === "string" ? { detail: problem.detail } : {}),
      ...(typeof problem.field === "string" ? { field: problem.field } : {}),
    };
  } catch {
    // Non-JSON error body - fall back to a generic message.
    return {};
  }
}

/**
 * The Organization is offline, not the session. There is nothing to retry and no login to restart:
 * the whole app is replaced by the screen that says so, in the words the state deserves.
 *
 * Closed belongs here beside suspended. A member still holding an unexpired token when their
 * Organization is closed meets exactly the same refusal, and reading only the suspended code left
 * them staring at per-query error states until a silent renew eventually rescued them.
 */
function notifyIfOrganizationOffline(status: number, code?: string): void {
  if (status !== 403) return;
  if (code === "organization_suspended") notifyOrganizationOffline("SUSPENDED");
  if (code === "organization_closed") notifyOrganizationOffline("CLOSED");
}

/** `RequestInit` with `headers` narrowed to a plain record: the header merge below
 *  spreads it, and spreading a `Headers` instance or a `string[][]` silently yields
 *  indices instead of headers. Every call site already passes an object literal. */
type ApiRequestInit = Omit<RequestInit, "headers"> & {
  headers?: Record<string, string>;
};

async function request(path: string, init?: ApiRequestInit): Promise<Response> {
  const token = getAccessToken();
  const isForm = init?.body instanceof FormData;
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    ...init,
    headers: {
      ...(isForm ? {} : { "content-type": "application/json" }),
      "x-api-version": API_VERSION,
      ...(token !== undefined ? { authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });

  if (response.ok) return response;

  const problem = await readProblem(response);
  if (response.status === 401) {
    // The token no longer convinces the backend - let the auth layer decide. The token sent and
    // the code travel with it: only the auth layer can tell an old token from a dead session, or a
    // revoked session from either.
    notifyUnauthorized(token, problem.code);
  }
  notifyIfOrganizationOffline(response.status, problem.code);
  throw ApiError.fromProblem(
    response.status,
    problem.detail ?? `Request to ${path} failed`,
    problem,
  );
}

export async function apiFetch<T>(
  path: string,
  init?: ApiRequestInit,
): Promise<T> {
  const response = await request(path, init);
  return (await response.json()) as T;
}

/** For a read whose "nothing there" is a 204 with no body: null then, the parsed JSON otherwise. */
export async function apiFetchOptional<T>(
  path: string,
  init?: ApiRequestInit,
): Promise<T | null> {
  const response = await request(path, init);
  if (response.status === 204) return null;
  return (await response.json()) as T;
}

export async function apiFetchVoid(
  path: string,
  init?: ApiRequestInit,
): Promise<void> {
  await request(path, init);
}

export interface DownloadedFile {
  blob: Blob;
  /** The server's suggested name, from `Content-Disposition`. Undefined only if
   *  the header is missing or unparseable - the caller picks its own fallback. */
  filename: string | undefined;
}

function filenameFromContentDisposition(
  header: string | null,
): string | undefined {
  if (header === null) return undefined;
  const match = /filename="?([^";]+)"?/i.exec(header);
  return match?.[1];
}

/**
 * For an endpoint that streams a file rather than returning JSON: a response
 * whose body is a blob and whose name travels in a header, not the payload.
 */
export async function apiFetchBlob(
  path: string,
  init?: ApiRequestInit,
): Promise<DownloadedFile> {
  const response = await request(path, init);
  const filename = filenameFromContentDisposition(
    response.headers.get("content-disposition"),
  );
  const blob = await response.blob();
  return { blob, filename };
}
