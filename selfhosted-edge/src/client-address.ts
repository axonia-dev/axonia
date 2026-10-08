import { isIP } from "node:net";

interface Caller {
  socketAddress: string | undefined;
  forwardedFor: string | undefined;
  trustedProxyHops: number;
}

// The three spellings proxies in front of an installation write besides a bare address: Azure
// Application Gateway appends the port to an IPv4 address, IPv6 with a port is bracketed, and a
// dual-stack socket reports an IPv4 caller mapped into IPv6.
const IPV4_WITH_PORT = /^(\d{1,3}(?:\.\d{1,3}){3}):\d+$/;
const BRACKETED_IPV6 = /^\[([^\]]+)\](?::\d+)?$/;
const MAPPED_IPV4 = /^::ffff:(\d{1,3}(?:\.\d{1,3}){3})$/i;

export const normalizedAddress = (raw: string): string | null => {
  const trimmed = raw.trim();
  const unwrapped =
    BRACKETED_IPV6.exec(trimmed)?.[1] ?? IPV4_WITH_PORT.exec(trimmed)?.[1] ?? trimmed;
  const address = MAPPED_IPV4.exec(unwrapped)?.[1] ?? unwrapped;
  return isIP(address) === 0 ? null : address.toLowerCase();
};

// X-Forwarded-For is read from the right, as many entries as there are trusted proxies: each proxy
// appends the address it was called from, so the entry the outermost trusted proxy wrote is the
// caller, and everything left of it is whatever the caller chose to send. With no proxy trusted the
// header is ignored outright, because then the caller wrote all of it.
export const clientAddress = ({
  socketAddress,
  forwardedFor,
  trustedProxyHops,
}: Caller): string => {
  const socket = normalizedAddress(socketAddress ?? "") ?? "";
  if (trustedProxyHops === 0 || forwardedFor === undefined) {
    return socket;
  }
  const entries = forwardedFor.split(",");
  const written = entries[Math.max(0, entries.length - trustedProxyHops)] ?? "";
  return normalizedAddress(written) ?? socket;
};
