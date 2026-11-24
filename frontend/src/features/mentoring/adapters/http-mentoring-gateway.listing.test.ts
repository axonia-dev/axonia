import { afterEach, describe, expect, it, vi } from "vitest";

import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { mentorFaceOf } from "@/features/mentoring/test-data";
import { bodyOf, jsonResponse, stubFetchJson, urlOf } from "@/test/fetch";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("other mentors", () => {
  it("readsAPageOfMentorsAndParsesThePagedModel", async () => {
    const fetchSpy = stubFetchJson({
      content: [mentorFaceOf()],
      page: { size: 20, number: 1, totalElements: 21, totalPages: 2 },
    });

    const result = await httpMentoringGateway.mentors("platform", 1);

    expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toBe(
      "/axonia/communities/platform/mentoring/mentors?page=1&size=20",
    );
    expect(result).toEqual({
      content: [mentorFaceOf()],
      totalElements: 21,
      totalPages: 2,
      number: 1,
      size: 20,
    });
  });

  it("putsTheListingWithOneContentTypeFromTheClient", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(null, 204));

    await httpMentoringGateway.setListing("platform", true);

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toBe("/axonia/communities/platform/mentoring/listing");
    expect(init?.method).toBe("PUT");
    expect(bodyOf(fetchSpy)).toEqual({ listed: true });
    expect(new Headers(init?.headers).get("Content-Type")).toBe(
      "application/json",
    );
  });
});
