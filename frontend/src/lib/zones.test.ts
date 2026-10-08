import { describe, expect, it } from "vitest";

import {
  cityOf,
  ignoreTypedZoneText,
  viewerZone,
  zoneOptions,
} from "@/lib/zones";

describe("cityOf", () => {
  it("names the place, without the underscore an identifier separates words with", () => {
    expect(cityOf("America/Los_Angeles")).toBe("Los Angeles");
  });

  it("keeps a zone that names no region", () => {
    expect(cityOf("UTC")).toBe("UTC");
  });
});

describe("viewerZone", () => {
  it("reads the clock the browser is set to", () => {
    // vite.config.ts pins TZ=America/Los_Angeles for exactly this kind of assertion.
    expect(viewerZone()).toBe("America/Los_Angeles");
  });
});

describe("zoneOptions", () => {
  it("offers UTC, which the engine's own enumeration leaves out", () => {
    expect(zoneOptions("")).toContainEqual({ id: "UTC", label: "UTC" });
  });

  it("includes the stored value whatever it is, so no zone goes unrepresented", () => {
    expect(zoneOptions("Etc/GMT+5")).toContainEqual({
      id: "Etc/GMT+5",
      label: "Etc/GMT+5",
    });
  });

  it("does not duplicate a stored value the list already has", () => {
    const berlin = zoneOptions("Europe/Berlin").filter(
      (option) => option.id === "Europe/Berlin",
    );

    expect(berlin).toHaveLength(1);
  });

  it("sorts so the field reads alphabetically", () => {
    const ids = zoneOptions("").map((option) => option.id);

    expect([...ids].sort((a, b) => a.localeCompare(b))).toEqual(ids);
  });
});

describe("ignoreTypedZoneText", () => {
  it("does nothing, because the field only ever commits through onSelect", () => {
    expect(ignoreTypedZoneText()).toBeUndefined();
  });
});
