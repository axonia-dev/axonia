import { describe, expect, it } from "vitest";

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";
import { recordOpening } from "@/features/knowledge/api/record-opening";

describe("recordOpening", () => {
  // The design's hardest rule: a failed count must be silence, never an interruption.
  // A rejected gateway call has to disappear without throwing synchronously and without
  // surfacing as an unhandled rejection - either one would reach the browser console (or,
  // worse, a dev-mode crash overlay) over a beacon nobody is watching.
  //
  // This stubs the gateway by assignment rather than `vi.fn()`/`vi.spyOn`: a vitest mock
  // attaches its own internal `.then`/`.catch` to track settled results, which marks the
  // returned promise as "handled" regardless of whether `record-opening.ts` itself calls
  // `.catch`. That instrumentation would make this test pass even with the real `.catch`
  // deleted, so a genuinely uninstrumented rejection is required to observe it.
  it("swallows a rejected beacon without throwing or leaving an unhandled rejection", async () => {
    let receivedArtifactId: number | undefined;
    const original = httpKnowledgeGateway.recordOpening;
    httpKnowledgeGateway.recordOpening = (artifactId: number) => {
      receivedArtifactId = artifactId;
      return Promise.reject(new Error("network down"));
    };

    try {
      expect(() => recordOpening(42)).not.toThrow();

      // Flushes the microtask queue (where the rejection settles) and a macrotask turn
      // (where an unhandled rejection is flagged), so a missing `.catch` has a chance to
      // surface as a vitest "Unhandled Rejection" failure before the test ends.
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(receivedArtifactId).toBe(42);
    } finally {
      httpKnowledgeGateway.recordOpening = original;
    }
  });
});
