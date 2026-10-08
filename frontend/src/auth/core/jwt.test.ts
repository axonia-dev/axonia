import { describe, expect, it } from "vitest";

import { decodeJwtPayload } from "./jwt";

const jwtWith = (payload: string) => `header.${payload}.signature`;

/** base64url: the "+/" alphabet swapped for "-_", and the padding dropped. */
const base64Url = (value: string) =>
  btoa(value).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");

describe("decodeJwtPayload", () => {
  it("reads the claims of a payload whose base64url padding was stripped", () => {
    // given - "aaa" encodes to four characters plus padding, so an unpadded segment is the
    // ordinary case rather than an odd one
    const token = jwtWith(base64Url(JSON.stringify({ sub: "aaa", n: 1 })));

    // when / then
    expect(decodeJwtPayload(token)).toEqual({ sub: "aaa", n: 1 });
  });

  it("decodes the base64url alphabet, not only base64", () => {
    // given - "?~" forces a "+" and a "/" into standard base64, which base64url spells "-" and "_"
    const token = jwtWith(base64Url(JSON.stringify({ sub: "a?~b" })));

    // when / then
    expect(decodeJwtPayload(token)).toEqual({ sub: "a?~b" });
  });

  it("is undefined for a string that is not a JWT at all", () => {
    expect(decodeJwtPayload("not-a-jwt")).toBeUndefined();
  });

  it("is undefined when the segment is not base64", () => {
    expect(decodeJwtPayload(jwtWith("###"))).toBeUndefined();
  });

  it("is undefined when the payload is valid JSON but not an object", () => {
    // given - a bare number parses without throwing, and claims read off it would all be undefined
    expect(decodeJwtPayload(jwtWith(base64Url("7")))).toBeUndefined();
  });

  it("is undefined when the payload is the JSON null literal", () => {
    expect(decodeJwtPayload(jwtWith(base64Url("null")))).toBeUndefined();
  });
});
