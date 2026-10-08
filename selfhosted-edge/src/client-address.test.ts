import { describe, expect, it } from "vitest";
import { clientAddress, normalizedAddress } from "./client-address.ts";

const SOCKET = "::ffff:10.0.0.5";
const CHAIN = "192.0.2.66, 198.51.100.1, 203.0.113.7";

describe("the caller's address", () => {
  it("is the socket's when no proxy is trusted, whatever the caller forwards", () => {
    expect(clientAddress({ socketAddress: SOCKET, forwardedFor: CHAIN, trustedProxyHops: 0 })).toBe(
      "10.0.0.5",
    );
  });

  it.each([
    [1, "203.0.113.7"],
    [2, "198.51.100.1"],
    [3, "192.0.2.66"],
  ])("is the entry the outermost of %i trusted proxies wrote", (hops, caller) => {
    expect(
      clientAddress({ socketAddress: SOCKET, forwardedFor: CHAIN, trustedProxyHops: hops }),
    ).toBe(caller);
  });

  it("is the left-most entry when the chain is shorter than the proxies trusted", () => {
    expect(clientAddress({ socketAddress: SOCKET, forwardedFor: CHAIN, trustedProxyHops: 5 })).toBe(
      "192.0.2.66",
    );
  });

  it("is the socket's when the trusted proxy forwarded nothing", () => {
    expect(
      clientAddress({ socketAddress: SOCKET, forwardedFor: undefined, trustedProxyHops: 1 }),
    ).toBe("10.0.0.5");
  });

  it("is the socket's when the entry the proxy wrote is not an address", () => {
    expect(
      clientAddress({ socketAddress: SOCKET, forwardedFor: "unknown", trustedProxyHops: 1 }),
    ).toBe("10.0.0.5");
  });

  it("is empty once the socket has gone, which the backend reads as no address", () => {
    expect(
      clientAddress({ socketAddress: undefined, forwardedFor: undefined, trustedProxyHops: 0 }),
    ).toBe("");
  });
});

describe("an address as a proxy writes it", () => {
  it.each([
    ["203.0.113.7", "203.0.113.7"],
    [" 203.0.113.7 ", "203.0.113.7"],
    ["203.0.113.7:51234", "203.0.113.7"],
    ["[2001:db8::7]:51234", "2001:db8::7"],
    ["[2001:DB8::7]", "2001:db8::7"],
    ["2001:db8::7", "2001:db8::7"],
    ["::ffff:203.0.113.7", "203.0.113.7"],
  ])("reads %j as %s", (written, address) => {
    expect(normalizedAddress(written)).toBe(address);
  });

  it.each(["", "unknown", "203.0.113", "203.0.113.7:port", "evil.example", "[203.0.113.7"])(
    "refuses %j",
    (written) => {
      expect(normalizedAddress(written)).toBeNull();
    },
  );
});
