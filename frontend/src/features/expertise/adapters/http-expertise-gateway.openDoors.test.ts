import { afterEach, describe, expect, it, vi } from "vitest";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { stubFetchJson, urlOf } from "@/test/fetch";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("httpExpertiseGateway - open doors", () => {
  it("openDoorsHitsTheOpenEndpointAndParses", async () => {
    // given
    const fetchSpy = stubFetchJson([
      {
        canonicalTag: "observability",
        latestDeclaredAt: "2026-11-10T09:00:00Z",
        people: [
          {
            userId: "u1",
            displayName: "Lea Moser",
            avatarUrl: null,
            declaredAt: "2026-11-10T09:00:00Z",
          },
        ],
      },
    ]);

    // when
    const actual = await httpExpertiseGateway.openDoors("guild");

    // then
    expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain(
      "/axonia/communities/guild/expertise/open",
    );
    expect(actual).toEqual([
      {
        canonicalTag: "observability",
        latestDeclaredAt: "2026-11-10T09:00:00Z",
        people: [
          {
            userId: "u1",
            displayName: "Lea Moser",
            avatarUrl: null,
            declaredAt: "2026-11-10T09:00:00Z",
          },
        ],
      },
    ]);
  });
});
