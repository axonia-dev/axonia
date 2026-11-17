export const mentoringKeys = {
  all: ["mentoring"] as const,
  community: (slug: string) =>
    [...mentoringKeys.all, "community", slug] as const,
  page: (slug: string) => [...mentoringKeys.community(slug), "page"] as const,
  mine: () => [...mentoringKeys.all, "mine"] as const,
  presence: () => [...mentoringKeys.all, "presence"] as const,
  journal: (id: number) => [...mentoringKeys.all, "journal", id] as const,
  words: () => [...mentoringKeys.all, "words"] as const,
};
