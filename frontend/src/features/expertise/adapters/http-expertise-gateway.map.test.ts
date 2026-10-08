import { afterEach, expect, test, vi } from "vitest";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { stubFetchJson, urlOf } from "@/test/fetch";

afterEach(() => vi.restoreAllMocks());

const payload = {
  topics: [
    {
      canonicalTag: "kafka",
      practitionerCount: 2,
      signalCount: 7,
      topHolderShare: 0.57,
      topHolderName: "Ana Reis",
      presentedCount: 6,
      contributedCount: 1,
      offeredCount: 0,
      lastActivityAt: "2026-05-01T00:00:00Z",
      openToQuestionsCount: 0,
    },
  ],
  links: [{ a: "debezium", b: "kafka", weight: 2 }],
};

test("map requests the community's topic map and parses it", async () => {
  const spy = stubFetchJson(payload);

  const result = await httpExpertiseGateway.map("platform-guild");

  expect(urlOf(spy.mock.calls[0]?.[0])).toContain(
    "/communities/platform-guild/expertise/map",
  );
  // A doubled Content-Type - the exact regression this repo has shipped before - reads back
  // as "application/json, application/json" once Headers folds the two entries together.
  const headers = new Headers(spy.mock.calls[0]?.[1]?.headers);
  expect(headers.get("content-type")).toBe("application/json");
  expect(result.topics[0]).toStrictEqual(payload.topics[0]);
  expect(result.links[0]).toStrictEqual(payload.links[0]);
});

test("map rejects a payload missing a required field", async () => {
  stubFetchJson({ topics: [{ canonicalTag: "kafka" }], links: [] });

  await expect(httpExpertiseGateway.map("platform-guild")).rejects.toThrow();
});
