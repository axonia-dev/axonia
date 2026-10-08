import { describe, expect, it } from "vitest";

import { describeNextOccurrence } from "@/features/events/domain/occurrence-preview";

describe("describeNextOccurrence", () => {
  it("states the occurrence in the series' own zone", () => {
    const text = describeNextOccurrence({
      startsOn: "2026-08-18",
      preferredTime: "18:00",
      timeZone: "Europe/Zurich",
      viewerZone: "Europe/Zurich",
    });

    expect(text).toBe("Next session Tue 18 Aug, 18:00");
  });

  it("adds the viewer's own reading only when the zones differ", () => {
    const text = describeNextOccurrence({
      startsOn: "2026-08-18",
      preferredTime: "18:00",
      timeZone: "Europe/Zurich",
      viewerZone: "Europe/London",
    });

    expect(text).toBe("Next session Tue 18 Aug, 18:00 · 17:00 in London");
  });

  it("applies the winter offset once the transition has passed", () => {
    const text = describeNextOccurrence({
      startsOn: "2026-11-17",
      preferredTime: "18:00",
      timeZone: "Europe/Zurich",
      viewerZone: "Europe/Zurich",
    });

    expect(text).toBe("Next session Tue 17 Nov, 18:00");
  });

  it("resolves a wall time before the spring-forward skip, on the transition day itself", () => {
    // Europe/Zurich moves 02:00 CET -> 03:00 CEST on 2026-03-29. 01:30 sits an
    // offset-guess UTC away from that jump, which is exactly where a single-pass
    // correction picks the wrong side of the transition.
    const text = describeNextOccurrence({
      startsOn: "2026-03-29",
      preferredTime: "01:30",
      timeZone: "Europe/Zurich",
      viewerZone: "Europe/Zurich",
    });

    expect(text).toBe("Next session Sun 29 Mar, 01:30");
  });

  it("resolves a wall time that occurs twice, on the fall-back transition day", () => {
    // Europe/Zurich moves 03:00 CEST -> 02:00 CET on 2026-10-25, so 01:30 happens
    // twice that day. The resolved instant must still read back as 01:30 local.
    const text = describeNextOccurrence({
      startsOn: "2026-10-25",
      preferredTime: "01:30",
      timeZone: "Europe/Zurich",
      viewerZone: "Europe/Zurich",
    });

    expect(text).toBe("Next session Sun 25 Oct, 01:30");
  });

  it("says nothing without a time to resolve", () => {
    expect(
      describeNextOccurrence({
        startsOn: "2026-08-18",
        preferredTime: "",
        timeZone: "Europe/Zurich",
        viewerZone: "Europe/Zurich",
      }),
    ).toBeNull();
  });
});
