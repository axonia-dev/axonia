import type { Practitioner } from "@/features/expertise/domain/expertise";

const WHO_OPTIONS = ["anyone", "demonstrated", "open"] as const;

/** Whom the search lists: anyone it found, people who practised the area here, or people open to questions on it. */
export type Who = (typeof WHO_OPTIONS)[number];

function keeps(who: Who, person: Practitioner): boolean {
  switch (who) {
    case "anyone":
      return true;
    case "demonstrated":
      return person.tier === "PRACTITIONER" || person.tier === "CONTRIBUTOR";
    case "open":
      return person.openToQuestions;
  }
}

/** A narrowing a link carries, or anyone for a value no narrowing has. */
export function parseWho(value: string | null): Who {
  return WHO_OPTIONS.find((who) => who === value) ?? "anyone";
}

export function filterByWho(people: Practitioner[], who: Who): Practitioner[] {
  return people.filter((person) => keeps(who, person));
}

export function countsByWho(people: Practitioner[]): Record<Who, number> {
  return {
    anyone: people.length,
    demonstrated: filterByWho(people, "demonstrated").length,
    open: filterByWho(people, "open").length,
  };
}
