export function useReadyCommunity() {
  return { ready: () => Promise.resolve(), prefetch: () => undefined };
}
