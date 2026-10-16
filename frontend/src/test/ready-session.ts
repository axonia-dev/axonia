export function useReadySession() {
  return { ready: () => Promise.resolve(), prefetch: () => undefined };
}
