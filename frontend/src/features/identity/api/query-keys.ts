export const identityKeys = {
  me: ["identity", "me"] as const,
  anyUsers: ["identity", "users"] as const,
  // Sorted copy so the same id set hits the same cache entry regardless of order.
  users: (ids: string[]) =>
    ["identity", "users", [...ids].sort((a, b) => a.localeCompare(b))] as const,
};
