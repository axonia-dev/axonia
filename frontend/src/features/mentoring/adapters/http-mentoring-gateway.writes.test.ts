import { afterEach, describe, expect, it, vi } from "vitest";

import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { bodyOf, jsonResponse, urlOf } from "@/test/fetch";

const topic = { label: "Kafka", area: "kafka" };
const note = { body: "Met", meetingOn: "2026-11-12", topicIds: [3] };
const placement = { state: "DONE" as const, position: 0 };
const pointerInput = { topicId: 1, noteId: null, url: "https://example.com" };
const g = httpMentoringGateway;

function sends(method: string, path: string, body?: unknown) {
  return { method, path, body };
}

describe("writes", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it.each([
    ["end", () => g.end(11), sends("POST", "/pairings/11/end")],
    [
      "addTopic",
      () => g.addTopic(11, topic),
      sends("POST", "/pairings/11/topics", topic),
    ],
    [
      "updateTopic",
      () => g.updateTopic(11, 3, topic),
      sends("PATCH", "/pairings/11/topics/3", topic),
    ],
    [
      "removeTopic",
      () => g.removeTopic(11, 3),
      sends("DELETE", "/pairings/11/topics/3"),
    ],
    [
      "placeTopic",
      () => g.placeTopic(11, 3, placement),
      sends("PUT", "/pairings/11/topics/3/placement", placement),
    ],
    [
      "addNote",
      () => g.addNote(11, note),
      sends("POST", "/pairings/11/notes", note),
    ],
    [
      "updateNote",
      () => g.updateNote(11, 4, note),
      sends("PATCH", "/pairings/11/notes/4", note),
    ],
    [
      "removeNote",
      () => g.removeNote(11, 4),
      sends("DELETE", "/pairings/11/notes/4"),
    ],
    [
      "addPointer",
      () => g.addPointer(11, pointerInput),
      sends("POST", "/pairings/11/pointers", pointerInput),
    ],
    [
      "removePointer",
      () => g.removePointer(11, 5),
      sends("DELETE", "/pairings/11/pointers/5"),
    ],
    [
      "writeWords",
      () => g.writeWords(11, "Thanks"),
      sends("PUT", "/pairings/11/words", { body: "Thanks" }),
    ],
    [
      "takeBackWords",
      () => g.takeBackWords(11),
      sends("DELETE", "/pairings/11/words"),
    ],
    [
      "openWords",
      () => g.openWords(11),
      sends("POST", "/pairings/11/words/open"),
    ],
    [
      "setNextMeeting",
      () => g.setNextMeeting(11, "2026-11-19"),
      sends("PUT", "/pairings/11/next-meeting", { on: "2026-11-19" }),
    ],
    [
      "clearNextMeeting",
      () => g.clearNextMeeting(11),
      sends("DELETE", "/pairings/11/next-meeting"),
    ],
  ])("%s sends its request", async (_name, call, expected) => {
    // given
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(jsonResponse(null, 204));

    // when
    const actual = await call();

    // then
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(`/axonia/mentoring${expected.path}`);
    expect(init?.method).toBe(expected.method);
    if (expected.body === undefined) expect(init?.body).toBeUndefined();
    else expect(bodyOf(fetchSpy)).toEqual(expected.body);
    expect(actual).toBeUndefined();
  });
});
