import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type Telemetry = typeof import("./faro");
type Initialize = typeof import("@grafana/faro-web-sdk").initializeFaro;
type Instrumentations =
  typeof import("@grafana/faro-web-sdk").getWebInstrumentations;

interface Loaded {
  initialize: ReturnType<typeof vi.fn<Initialize>>;
  setView: ReturnType<typeof vi.fn<(view: { name: string }) => void>>;
}

// Every case here resets the module registry and re-imports, and the load path is a dynamic import
// behind an idle callback. That work is scheduler-bound, not compute-bound, so under the full suite
// it loses to ~890 other files often enough to time out at the 5s default while passing in 2s
// alone. The number is a ceiling on a hang, not a performance budget, so it is set well clear of
// the worst scheduling delay rather than near it: 20s was breached once the suite grew past 800
// files and a second gate shared the machine.
vi.setConfig({ testTimeout: 60_000 });

// faro.ts holds the pending view and the resolved api in module state, and nothing in the module
// clears them. Re-importing it for each test removes that whole class of leak rather than
// compensating for one instance: a case added anywhere can call reportView with no load() in front
// of it and still start from a module that has never seen a view or an api.
let telemetry: Telemetry;

beforeEach(async () => {
  vi.resetModules();
  telemetry = await import("./faro");
});

afterEach(() => {
  vi.unstubAllGlobals();
});

// Waits for the load itself rather than polling for its effect under a deadline. Only faro-web-sdk is
// replaced: faro-web-tracing is imported for real, because a test below reads TracingInstrumentation's
// own name and a double would make that assertion a tautology. Its first import costs real time, and on a
// loaded machine it outran a one-second poll; the load then outlived its test and settled during the
// next one, which failed too. Awaiting the same two imports faro.ts starts takes the slow part off the
// clock, and the poll after it only covers the microtasks between them settling and faro.ts calling Faro.
// The lint rule against importing Faro exempts this file: a test never lands in a bundle chunk.
const loadSettled = async (initialize: Loaded["initialize"]): Promise<void> => {
  await Promise.all([
    import("@grafana/faro-web-sdk"),
    import("@grafana/faro-web-tracing"),
  ]);
  await vi.waitFor(() => {
    expect(initialize).toHaveBeenCalled();
  });
};

// Drives one full load: Faro's own module is replaced, requestIdleCallback runs its callback
// straight away, and the promise settles before the assertions read the call.
const load = async (
  instrumentations: Instrumentations = () => [],
  setView = vi.fn<(view: { name: string }) => void>(),
): Promise<Loaded> => {
  const initialize = vi.fn<Initialize>(
    () => ({ api: { setView } }) as unknown as ReturnType<Initialize>,
  );
  vi.doMock("@grafana/faro-web-sdk", () => ({
    initializeFaro: initialize,
    getWebInstrumentations: instrumentations,
  }));
  vi.stubGlobal("requestIdleCallback", (callback: () => void) => {
    callback();
  });

  telemetry.startTelemetry();
  await loadSettled(initialize);

  return { initialize, setView };
};

describe("startTelemetry", () => {
  it("does not load Faro during the first paint", () => {
    const idle = vi.fn();
    vi.stubGlobal("requestIdleCallback", idle);

    telemetry.startTelemetry();

    expect(idle).toHaveBeenCalledOnce();
  });

  it("defers behind a two second timer where requestIdleCallback is unavailable", () => {
    const timer = vi.fn();
    vi.stubGlobal("requestIdleCallback", undefined);
    vi.stubGlobal("setTimeout", timer);

    telemetry.startTelemetry();

    expect(timer).toHaveBeenCalledWith(expect.any(Function), 2_000);
  });

  // An absolute url on its own origin, never a vendor host and never the bare path. Faro excludes
  // its own collector from tracing by exact string comparison against a url OTel has already
  // resolved to an absolute href, so a bare "/telemetry" would never match and every delivery
  // would trace itself into a permanent loop.
  it("posts to an absolute url on its own origin", async () => {
    const { initialize } = await load();

    expect(initialize.mock.calls[0]![0].url).toBe(
      `${location.origin}/telemetry`,
    );
  });

  it("identifies itself as axonia-web", async () => {
    const { initialize } = await load();

    expect(initialize.mock.calls[0]![0].app.name).toBe("axonia-web");
  });

  // The browser-to-backend trace spine is most of what Faro is here for, and web-tracing is most of
  // its weight, so it is exactly what a later bundle-size sweep would drop.
  it("wires the tracing instrumentation", async () => {
    const { initialize } = await load();

    const names = (initialize.mock.calls[0]![0].instrumentations ?? []).map(
      (instrumentation) => instrumentation.name,
    );
    expect(names).toContain("@grafana/faro-web-tracing");
  });

  // Console arguments are unredactable free text and the edge scrub deliberately does not look
  // inside strings, so a console.error naming a person would reach Grafana whole.
  it("leaves console capture off", async () => {
    const instrumentations = vi.fn<Instrumentations>(() => []);
    await load(instrumentations);

    expect(instrumentations).toHaveBeenCalledWith({ captureConsole: false });
  });
});

describe("reportView", () => {
  it("collapses a route to its first segment before reporting it", async () => {
    const { setView } = await load();

    telemetry.reportView("/");
    telemetry.reportView("/communities/platform");
    telemetry.reportView("/discover");

    expect(setView.mock.calls.map(([view]) => view.name)).toEqual([
      "/",
      "/communities/*",
      "/discover",
    ]);
  });

  // The router navigates long before the deferred load finishes, so a view reported in that window
  // has to survive until there is an api to hand it to.
  it("replays the view reported before Faro loaded", async () => {
    telemetry.reportView("/discover");

    const { setView } = await load();

    expect(setView).toHaveBeenCalledWith({ name: "/discover" });
  });

  // TelemetryView's effect is mounted directly under BrowserRouter, above the entire route table,
  // and neither app has an ErrorBoundary, componentDidCatch or getDerivedStateFromError anywhere.
  // So a throw out of Faro here does not degrade telemetry, it unmounts the application and leaves
  // the member looking at a blank page. The mock throws by plain implementation rather than by a
  // rejected promise, so nothing else can be attaching a handler that hides the escape.
  it("survives a Faro api that throws", async () => {
    const setView = vi.fn<(view: { name: string }) => void>(() => {
      throw new Error("faro exploded");
    });
    await load(() => [], setView);

    expect(() => {
      telemetry.reportView("/discover");
    }).not.toThrow();
    expect(setView).toHaveBeenCalledOnce();
  });
});

describe("startTelemetry when Faro cannot start", () => {
  // Faro is the thing that would normally report a failure, so its own failure has nowhere to go:
  // a chunk retired by a deploy while a tab is open rejects the import, and a start that throws
  // rejects the same promise. Vitest fails a run on an unhandled rejection, which is what makes
  // this an assertion rather than a description: drop the catch in faro.ts and this reports one.
  //
  // Awaiting the load rather than a timer is what keeps the rejection inside this test, and keeps
  // this mock out of whichever test runs next.
  it("swallows the failure rather than leaving it unhandled", async () => {
    const initialize = vi.fn<Initialize>(() => {
      throw new Error("collector unreachable");
    });
    vi.doMock("@grafana/faro-web-sdk", () => ({
      initializeFaro: initialize,
      getWebInstrumentations: () => [],
    }));
    vi.stubGlobal("requestIdleCallback", (callback: () => void) => {
      callback();
    });

    expect(() => {
      telemetry.startTelemetry();
    }).not.toThrow();

    await loadSettled(initialize);
  });
});
