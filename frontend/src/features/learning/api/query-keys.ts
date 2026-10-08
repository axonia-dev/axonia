export const learningKeys = {
  all: ["learning"] as const,
  community: (slug: string) => [...learningKeys.all, slug] as const,
  page: (slug: string) => [...learningKeys.community(slug), "page"] as const,
  learners: (slug: string, tag: string) =>
    [...learningKeys.community(slug), "learners", tag] as const,
  removal: (slug: string) =>
    [...learningKeys.community(slug), "remove-intent"] as const,
};
