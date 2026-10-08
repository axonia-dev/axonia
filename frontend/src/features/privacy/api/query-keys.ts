export const privacyKeys = {
  all: ["privacy"] as const,
  erasurePreflight: () => [...privacyKeys.all, "erasure-preflight"] as const,
};
