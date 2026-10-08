import { describe, expect, it } from "vitest";

import { normalizeTag } from "@/lib/normalize-tag";

describe("normalizeTag", () => {
  it("lowercases", () => {
    expect(normalizeTag("Kafka")).toBe("kafka");
  });

  it("collapses a run of non-alphanumerics into one dash", () => {
    expect(normalizeTag("Apache Kafka!")).toBe("apache-kafka");
    expect(normalizeTag("domain -- driven   design")).toBe(
      "domain-driven-design",
    );
  });

  it("strips accents rather than dashing them", () => {
    expect(normalizeTag("café")).toBe("cafe");
    expect(normalizeTag("Événement")).toBe("evenement");
  });

  it("trims the dashes off both ends", () => {
    expect(normalizeTag("  kafka  ")).toBe("kafka");
    expect(normalizeTag("-kafka-")).toBe("kafka");
  });

  it("keeps digits", () => {
    expect(normalizeTag("Java 26")).toBe("java-26");
  });

  it("normalizes a name of pure punctuation to nothing", () => {
    expect(normalizeTag("!!!")).toBe("");
  });
});
