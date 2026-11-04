import { QueryClient } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { sessionKeys } from "@/features/events/api/query-keys";
import { useTitleMorphId } from "@/features/events/ui/title-morph";
import { inQueryClient } from "@/test/query-client";
import { aScheduledSession } from "@/test/session-fixture";

function loaded(displayTitle: string) {
  const client = new QueryClient();
  client.setQueryData(
    sessionKeys.detail(4),
    aScheduledSession({ id: 4, displayTitle }),
  );
  return client;
}

describe("useTitleMorphId", () => {
  it("morphs a title into the page that heads the session with the same name", () => {
    // when
    const { result } = renderHook(
      () => useTitleMorphId(4, "S26E10 - DevTool", "session-4"),
      inQueryClient(loaded("S26E10 - DevTool")),
    );

    // then
    expect(result.current).toBe("session-4");
  });

  it("keeps the title still when the page heads the session with another name", () => {
    // when
    const { result } = renderHook(
      () => useTitleMorphId(4, "P1s", "session-4"),
      inQueryClient(loaded("S26E10 - DevTool")),
    );

    // then
    expect(result.current).toBeUndefined();
  });

  it("names the title while the session is not loaded, as nothing can morph until it is", () => {
    // when
    const { result } = renderHook(
      () => useTitleMorphId(4, "P1s", "session-4"),
      inQueryClient(),
    );

    // then
    expect(result.current).toBe("session-4");
  });

  it("never reads a session for a projected occurrence", () => {
    // when
    const { result } = renderHook(
      () => useTitleMorphId(null, "Backend Guild", undefined),
      inQueryClient(),
    );

    // then
    expect(result.current).toBeUndefined();
  });
});
