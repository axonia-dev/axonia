/** Where a notification row goes when it is opened.
 *
 *  `actionUrl` arrives absolute: the backend mints it from its own public base URL
 *  (`https://app.example.com/communities/acme/sessions/123`), because the same string also has to work
 *  in mail and in a chat platform. Handing that to a router `Link` would push the whole URL on as
 *  a path, so a URL on this origin is reduced to the path the router actually knows. Anything on
 *  another origin is a real outbound link, and an entry with no usable destination has none: the
 *  row must not offer a click that leads nowhere. */
export type NotificationDestination =
  | { kind: "internal"; to: string }
  | { kind: "external"; href: string }
  | { kind: "none" };

const NONE: NotificationDestination = { kind: "none" };

/** `origin` is passed in rather than read from `window` so this stays pure and can be pinned
 *  against the real production origin in a test. */
export function destinationOf(
  actionUrl: string | null | undefined,
  origin: string,
): NotificationDestination {
  const raw = actionUrl?.trim();
  if (raw === undefined || raw === "") return NONE;
  const url = parse(raw, origin);
  if (url === undefined) return NONE;
  // Everything else a URL can carry - `javascript:` above all - is not a destination a row may
  // ever navigate to, whatever wrote the row.
  if (url.protocol !== "http:" && url.protocol !== "https:") return NONE;
  if (url.origin !== origin) return { kind: "external", href: url.href };
  return { kind: "internal", to: `${url.pathname}${url.search}${url.hash}` };
}

function parse(raw: string, origin: string): URL | undefined {
  try {
    return new URL(raw, origin);
  } catch {
    return undefined;
  }
}
