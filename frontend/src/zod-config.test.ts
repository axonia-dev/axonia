import { describe, expect, it } from "vitest";

describe("zod jitless configuration", () => {
  it("runs before any schema is constructed, so Zod's eval probe never fires", async () => {
    // Reproduces the load-bearing order documented in boot.ts: import ./zod-config,
    // then construct a schema, the same sequence the app's real import graph must
    // preserve. Zod's probe wraps `new Function("")` in a try/catch, so it never
    // throws observably - the only honest way to prove it did not run is to watch the
    // real Function constructor, which is public, observable behaviour, not a Zod
    // internal. A companion assertion below also checks Zod's own public readback.
    let probed = false;
    const OriginalFunction = globalThis.Function;
    globalThis.Function = new Proxy(OriginalFunction, {
      construct(target, args) {
        probed = true;
        return Reflect.construct(
          target,
          args as ConstructorParameters<typeof Function>,
        );
      },
    });

    try {
      await import("./zod-config");
      const { z } = await import("zod");

      expect(z.config().jitless).toBe(true);

      z.object({ ok: z.string() });
    } finally {
      globalThis.Function = OriginalFunction;
    }

    expect(probed).toBe(false);
  });
});
