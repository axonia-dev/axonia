import { afterEach, describe, expect, it, vi } from "vitest";

import { httpLearningGateway } from "@/features/learning/adapters/http-learning-gateway";
import { bodyOf, jsonResponse, stubFetchJson, urlOf } from "@/test/fetch";

function methodOf(init: RequestInit | undefined): string {
  return init?.method ?? "GET";
}

describe("httpLearningGateway", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("page", () => {
    it("reads the community's learning page and parses it", async () => {
      // given
      const page = {
        member: true,
        archived: false,
        limit: 10,
        mine: [{ tag: "kafka", declaredAt: "2026-09-25T12:00:00Z" }],
        wanted: [{ tag: "kafka", learners: 3, canBeAsked: 2 }],
      };
      const fetchSpy = stubFetchJson(page);

      // when
      const actual = await httpLearningGateway.page("platform");

      // then
      const [input, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(input)).toContain("/axonia/communities/platform/learning");
      expect(methodOf(init)).toBe("GET");
      expect(actual).toEqual(page);
    });

    it("refuses a page that does not match the contract", async () => {
      // given
      stubFetchJson({ member: true, mine: [] });

      // when
      const actual = httpLearningGateway.page("platform");

      // then
      await expect(actual).rejects.toThrow();
    });
  });

  describe("learners", () => {
    it("reads the learners of an area by its encoded tag", async () => {
      // given
      const learner = {
        userId: "u-1",
        displayName: "Ada",
        avatarUrl: null,
        declaredAt: "2026-09-25T12:00:00Z",
      };
      const fetchSpy = stubFetchJson([learner]);

      // when
      const actual = await httpLearningGateway.learners("platform", "c++");

      // then
      expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain(
        "/axonia/communities/platform/learning/areas/c%2B%2B/learners",
      );
      expect(actual).toEqual([learner]);
    });
  });

  describe("add", () => {
    it("posts the typed tag with a single Content-Type and parses the stored area", async () => {
      // given
      const fetchSpy = stubFetchJson({
        tag: "kafka",
        declaredAt: "2026-09-25T12:00:00Z",
      });

      // when
      const actual = await httpLearningGateway.add("platform", "Kafka");

      // then
      const [input, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(input)).toContain(
        "/axonia/communities/platform/learning/intents",
      );
      expect(methodOf(init)).toBe("POST");
      expect(bodyOf(fetchSpy)).toEqual({ tag: "Kafka" });
      expect(new Headers(init?.headers).get("content-type")).toBe(
        "application/json",
      );
      expect(actual).toEqual({
        tag: "kafka",
        declaredAt: "2026-09-25T12:00:00Z",
      });
    });
  });

  describe("remove", () => {
    it("deletes an intent by its encoded tag", async () => {
      // given
      const fetchSpy = vi
        .spyOn(globalThis, "fetch")
        .mockResolvedValue(jsonResponse(null, 204));

      // when
      const actual = await httpLearningGateway.remove("platform", "c++");

      // then
      const [input, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(input)).toContain(
        "/axonia/communities/platform/learning/intents/c%2B%2B",
      );
      expect(methodOf(init)).toBe("DELETE");
      expect(actual).toBeUndefined();
    });
  });
});
