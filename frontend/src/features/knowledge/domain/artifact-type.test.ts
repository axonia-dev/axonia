import { describe, expect, it } from "vitest";

import { artifactTypeSchema } from "@/features/knowledge/domain/artifact-type";

const recording = {
  id: 1,
  label: "Recording",
  icon: null,
  accentColor: null,
  artifactCount: 0,
};

describe("artifactTypeSchema", () => {
  it("parses a valid artifact type", () => {
    // when
    const actual = artifactTypeSchema.parse(recording);

    // then
    expect(actual).toEqual(recording);
  });

  it("rejects a type without a label", () => {
    // given
    const invalid = { id: 1 };

    // when / then
    expect(() => artifactTypeSchema.parse(invalid)).toThrow();
  });
});
