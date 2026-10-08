import { afterEach, expect, test, vi } from "vitest";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { stubFetchJson, urlOf } from "@/test/fetch";

afterEach(() => vi.restoreAllMocks());

test("mapContributions parses the payload and calls the community-scoped path", async () => {
  const spy = stubFetchJson({
    people: [{ userId: "u1", displayName: "Ada Lovelace", avatarUrl: null }],
    items: [
      {
        signalId: 1,
        canonicalTag: "kafka",
        userId: "u1",
        reach: 42,
        evidence: {
          signalType: "PRESENTED",
          sourceType: "SLOT",
          sourceId: 10,
          label: "Scaling Kafka",
          communitySlug: "backend-guild",
          occurredAt: "2026-09-01T10:00:00Z",
          sessionId: 5,
          typeId: 2,
          typeLabel: "Workshop",
          typeIcon: "wrench",
          typeAccentColor: "#123456",
        },
      },
    ],
    truncated: false,
  });

  const result = await httpExpertiseGateway.mapContributions("backend-guild");

  expect(urlOf(spy.mock.calls[0]?.[0])).toContain(
    "/axonia/communities/backend-guild/expertise/map/contributions",
  );
  const headers = new Headers(spy.mock.calls[0]?.[1]?.headers);
  expect(headers.get("content-type")).toBe("application/json");
  expect(result.people[0]?.displayName).toBe("Ada Lovelace");
  expect(result.items[0]?.reach).toBe(42);
  expect(result.items[0]?.evidence.sessionId).toBe(5);
});

test("mapContributions accepts a contribution whose reach is unknown", async () => {
  stubFetchJson({
    people: [],
    items: [
      {
        signalId: 2,
        canonicalTag: "kafka",
        userId: "u1",
        reach: null,
        evidence: {
          signalType: "CONTRIBUTED",
          sourceType: "ARTIFACT",
          sourceId: 20,
          label: null,
          communitySlug: "backend-guild",
          occurredAt: "2026-09-01T10:00:00Z",
        },
      },
    ],
    truncated: true,
  });

  const result = await httpExpertiseGateway.mapContributions("backend-guild");

  expect(result.items[0]?.reach).toBeNull();
  expect(result.items[0]?.evidence.sessionId).toBeNull();
  expect(result.truncated).toBe(true);
});

test("mapContributions rejects a payload missing a required field", async () => {
  stubFetchJson({ people: [], items: [{ signalId: 3 }], truncated: false });

  await expect(
    httpExpertiseGateway.mapContributions("backend-guild"),
  ).rejects.toThrow();
});
