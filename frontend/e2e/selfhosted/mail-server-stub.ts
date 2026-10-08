import type { Page, Request } from "@playwright/test";

export const OWNER = [
  "communication:write",
  "mail_server:read",
  "mail_server:write",
];
const UNSET = { value: null, lockedBy: null };

const NOT_CONFIGURED = {
  state: "not_configured",
  revision: 0,
  host: UNSET,
  port: UNSET,
  security: UNSET,
  username: UNSET,
  password: { set: false, readable: true, lockedBy: null },
  fromAddress: UNSET,
  fromName: UNSET,
  changedSinceTest: false,
  lastFailure: null,
  lastChange: null,
};

export const CONFIGURED = {
  ...NOT_CONFIGURED,
  state: "configured",
  revision: 1,
  host: { value: "mailpit", lockedBy: null },
  port: { value: 1025, lockedBy: null },
  security: { value: "NONE", lockedBy: null },
  fromAddress: { value: "axonia@acme.example", lockedBy: null },
  lastChange: { occurredAt: "2026-10-02T12:03:00.000Z", actorName: "Dev User" },
};

export const STORED_PASSWORD = {
  ...CONFIGURED,
  username: { value: "axonia", lockedBy: null },
  password: { set: true, readable: true, lockedBy: null },
};

/** A relay name as long as real ones get, with no break opportunity but its dots. */
export const LONG_HOST =
  "smtp-relay-01.eu-west-1.internal.mail-gateway.acme-communities.example";

export const LOCKED_LONG_HOST = {
  ...CONFIGURED,
  host: { value: LONG_HOST, lockedBy: "AXONIA_MAIL_HOST" },
};

function json(body: unknown) {
  return {
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(body),
  };
}

/** The installation's mail server: `initial` until a save, then what the save answered. Returns
 *  every write, in order. Registered after the shell, so it answers first. */
export async function stubMailServer(
  page: Page,
  initial: object = NOT_CONFIGURED,
): Promise<Request[]> {
  const writes: Request[] = [];
  let view = initial;
  await page.route("**/axonia/roundup/settings", (route) =>
    route.fulfill(json({ organizationId: "default", enabled: false })),
  );
  await page.route("**/axonia/mail-server**", (route) => {
    const request = route.request();
    const { pathname } = new URL(request.url());
    if (request.method() !== "GET") writes.push(request);
    if (pathname.endsWith("/test")) {
      return route.fulfill(
        json({
          accepted: true,
          host: "mailpit",
          acceptedAt: "2026-10-02T12:02:00.000Z",
          testToken: "v1.token",
          expiresAt: "2026-10-02T12:12:00Z",
        }),
      );
    }
    if (request.method() === "PUT") {
      view = CONFIGURED;
      return route.fulfill(json(CONFIGURED));
    }
    if (pathname.endsWith("/status")) {
      return route.fulfill(json({ configured: view !== NOT_CONFIGURED }));
    }
    return route.fulfill(json(view));
  });
  return writes;
}

export const NOTIFICATIONS = "/settings/organization/notifications";
