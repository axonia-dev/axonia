/** Shared fetch-adapter test helpers: build a JSON Response and read the URL of a fetch call. */

import { type MockInstance, vi } from "vitest";

const NO_BODY_STATUSES = new Set([204, 205, 304]);

export function jsonResponse(body: unknown, status = 200): Response {
  // The Response constructor throws if a no-content status carries a body at all.
  const responseBody = NO_BODY_STATUSES.has(status)
    ? null
    : JSON.stringify(body);
  return new Response(responseBody, {
    status,
    headers: { "content-type": "application/json" },
  });
}

export function urlOf(input: RequestInfo | URL | undefined): string {
  if (input instanceof Request) {
    return input.url;
  }
  return input instanceof URL ? input.href : (input ?? "");
}

/** Spies on `fetch` and resolves every call with the given JSON body - the
 *  common case for gateway tests that only need to stub one response. */
export function stubFetchJson(
  body: unknown,
  status = 200,
): MockInstance<typeof fetch> {
  const fetchSpy = vi.spyOn(globalThis, "fetch");
  fetchSpy.mockResolvedValue(jsonResponse(body, status));
  return fetchSpy;
}

/** Reads the JSON request body of a recorded `fetch` call. Fails the call rather
 *  than short-circuiting to `undefined`, so a missing call reports itself. */
export function bodyOf<T>(spy: MockInstance<typeof fetch>, call = 0): T {
  const init = spy.mock.calls[call]?.[1];
  if (init?.body === undefined || init.body === null) {
    throw new Error(`fetch call ${call} carried no body`);
  }
  return JSON.parse(init.body as string) as T;
}
