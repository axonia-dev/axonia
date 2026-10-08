// Loaded once the main thread first goes idle, or after two seconds on an engine that has no
// requestIdleCallback, rather than on the critical path. Faro with web-tracing is roughly 40-60kB
// gzipped, which is a real cost on a landing page that has been tuned. The trade accepted in the
// design is losing telemetry for errors in the first few hundred milliseconds of a page's life, in
// exchange for not regressing LCP. A static import would undo that no matter what this function
// does, so the two dynamic imports below are load-bearing rather than stylistic, and an eslint
// no-restricted-imports rule fails the build on any static or type-only import of @grafana/faro-*.
// No test can see that hoisting happen; only the rule and the build log can.
//
// Faro core without tracing would be a fraction of the size, but it would break the
// browser-to-backend trace spine, which is most of the value. That was considered and rejected.
//
// The collector is a path on axonia's own origin rather than a vendor host: the edge Worker
// receives it, scrubs it and forwards it. That is what keeps `connect-src 'self'` sufficient,
// keeps the Grafana credential out of this bundle entirely, and keeps a blocker from recognising a
// telemetry vendor. The scrubbing lives in that one server-side place - filtering here as well
// would be a second copy to keep in step with it, and a browser cannot be trusted to have run it
// anyway.
//
// Locally the Worker is not in front of the dev server, so the dev server answers /telemetry with
// the 204 the Worker gives when no collector is configured, and browser telemetry goes nowhere.
// That mirrors the backend exporting nothing outside production, and it is why this cannot be
// verified by running the app locally.

const COLLECTOR_PATH = "/telemetry";

// Absolute rather than the bare path, and that is not cosmetic. Faro hands its own collector URL to
// the tracer so the delivery request is not itself traced: FetchTransport.getIgnoreUrls() returns
// [options.url], TracingInstrumentation forwards it into OTel's FetchInstrumentation, and OTel
// compares it with === against a URL it has already resolved to an absolute href. A bare path never
// matches. Every batch POST would then be traced, the span would be exported, and exporting it
// would post another batch - roughly one request per second out of an idle tab, for as long as the
// tab stays open, each one also producing a POST /telemetry span at the edge.
//
// Building the string with new URL() rather than by concatenation is what makes the two sides
// identical: OTel resolves through the same constructor, so the round trip is exact by
// construction rather than by both spellings happening to agree.
const collectorUrl = (): string =>
  new URL(COLLECTOR_PATH, location.origin).href;

// The first path segment, with a trailing /* when the path carried more: /communities/platform
// becomes /communities/*, /discover and / stay whole. This is the same rule as collapsedRoute in
// edge/src/telemetry.ts, so a browser view and an edge span name the same string for the same
// navigation.
//
// It has to happen here rather than at the edge. The Worker collapses a string that is entirely an
// http address, which covers meta.page.url, but a view name is a bare path and would pass through
// untouched - /invite/<code> is a working join link, and it would reach Grafana whole.
//
// The cost is large, and larger than the shape of the rule suggests. Against the real route table
// in routes/app-routes.tsx, depth-1 collapse folds roughly twenty-one authenticated routes into the
// single name /communities/*: /communities, /communities/new, /communities/kinds, and every section
// of every community - backlog, library, schedule, sessions/:sessionId,
// series/:seriesId/occurrences/:date, expertise, expertise/curation, health, edit, look-and-feel and
// the three settings pages - plus the whole settings subtree mounted at /communities/:slug. A
// signed-in session spends nearly all of its time under that one name, and a signed-in session is
// the overwhelming majority, so errors grouped by view is close to a constant rather than a
// dimension. setView de-duplicates by name on top of that, so moving from backlog to library to
// schedule raises no view-changed event at all.
//
// That is landed rather than fixed here because the fix is a router change, not a telemetry one:
// lift the route tree into one RouteObject[] consumed by both useRoutes and
// matchRoutes(routes, location), so the matched pattern comes from the same array the router
// renders and cannot drift from it, then replace each :param with *. That restructures a live
// router and deserves its own change with its own review. Writing a route table out here instead
// would be a second list to forget, which is the failure the edge helper's own comment argues
// against.
const viewName = (pathname: string): string => {
  const segments = pathname.split("/").filter((segment) => segment !== "");
  const first = segments[0];
  if (first === undefined) {
    return "/";
  }
  return segments.length > 1 ? `/${first}/*` : `/${first}`;
};

// The router can navigate before Faro has finished loading - that is the point of the deferral - so
// the latest view is held here and replayed once the API exists.
let latestView: string | undefined;
let publishView: ((name: string) => void) | undefined;

// The one call this file makes into vendor code from a React effect, and the effect is mounted
// above the whole route table with no error boundary anywhere in the app, so a throw out of
// setView would unmount every route and leave a blank page. Every other surface swallows its own
// telemetry failure - emitSpan, handleTelemetry and startTelemetry's own load all do - and this
// was the exception. Nothing is logged: the reporter is Faro, and Faro is what just failed.
export const reportView = (pathname: string): void => {
  latestView = viewName(pathname);
  try {
    publishView?.(latestView);
  } catch {
    // Telemetry must not be able to take the application down.
  }
};

const idle = (run: () => void): void => {
  if (typeof requestIdleCallback === "function") {
    requestIdleCallback(run);
    return;
  }
  setTimeout(run, 2_000);
};

export const startTelemetry = (): void => {
  idle(() => {
    void (async () => {
      const [
        { initializeFaro, getWebInstrumentations },
        { TracingInstrumentation },
      ] = await Promise.all([
        import("@grafana/faro-web-sdk"),
        import("@grafana/faro-web-tracing"),
      ]);

      // captureConsole: false. ConsoleInstrumentation ships the arguments of every console.error,
      // warn and info verbatim into logs, and the Worker's scrub deliberately does not look inside
      // strings, so console.error("failed for jane@example.com") would reach Grafana whole. No
      // signal may carry a person's address or name, and this is the one path where
      // nothing downstream can enforce it. Uncaught errors and unhandled rejections are captured by
      // ErrorsInstrumentation regardless, so what is given up is deliberate console calls, which
      // are not the incident signal. Turn this back on only together with a redaction pass.
      const faro = initializeFaro({
        url: collectorUrl(),
        app: { name: "axonia-web" },
        instrumentations: [
          ...getWebInstrumentations({ captureConsole: false }),
          new TracingInstrumentation(),
        ],
      });

      publishView = (name) => faro.api.setView({ name });
      if (latestView !== undefined) {
        publishView(latestView);
      }
      // A deploy can retire the chunk this page would load while a tab is still open. Without this
      // the import rejects into nothing, and the one thing that would normally report it is Faro,
      // which is precisely what failed to load. Matches edge/src/telemetry.ts.
    })().catch(() => undefined);
  });
};
