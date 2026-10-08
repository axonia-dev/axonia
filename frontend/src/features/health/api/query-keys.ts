export const healthKeys = {
  all: ["health"] as const,
  snapshot: (slug: string, window: string) =>
    [...healthKeys.all, slug, window] as const,
};
