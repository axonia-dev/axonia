import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";

import { SearchField } from "@/components/ui/SearchField";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { useExpertiseSearch } from "@/features/expertise/api/use-expertise-search";
import { CommunityKnows } from "@/features/expertise/ui/CommunityKnows";
import { OpenDoorsShelf } from "@/features/expertise/ui/OpenDoorsShelf";
import { PractitionerList } from "@/features/expertise/ui/PractitionerList";
import {
  countsByWho,
  filterByWho,
  parseWho,
  type Who,
} from "@/features/expertise/ui/who-filter";
import { LearnersBridge } from "@/features/learning/ui/LearnersBridge";
import { useDebouncedValue } from "@/lib/use-debounced-value";

const DEBOUNCE_MS = 300;

const WHO = "who";

function option(id: Who, label: string, count: number | undefined) {
  return count === undefined ? { id, label } : { id, label, count };
}

function whoOptions(counts: Record<Who, number> | undefined) {
  return [
    option("anyone", "Anyone", counts?.anyone),
    option("demonstrated", "Practised", counts?.demonstrated),
    option("open", "Open to questions", counts?.open),
  ];
}

/** A null value leaves the parameter out, so a default never reaches the URL. */
function write(params: URLSearchParams, key: string, value: string | null) {
  if (value === null) {
    params.delete(key);
  } else {
    params.set(key, value);
  }
}

/** The shelf offers a first question, so it gives way the moment one is typed. */
function nothingTyped(input: string, term: string): boolean {
  return input.trim() === "" && term === "";
}

export function ExpertiseSearchView() {
  const { slug = "" } = useParams();
  const [params, setParams] = useSearchParams();
  const [input, setInput] = useState(params.get("term") ?? "");
  const [term, setTerm] = useState(() => input.trim());
  const [who, setWho] = useState<Who>(() =>
    term === "" ? "anyone" : parseWho(params.get(WHO)),
  );
  const debouncedInput = useDebouncedValue(input, DEBOUNCE_MS);

  // `term` also gets instant writes below (askAbout); this only applies a later debounced
  // value once it moves, the same derived-state-during-render pattern as CountryComboBox. The
  // mount value is seeded directly into `term` above, so a `?term=` deep link searches right away.
  const [seenDebounced, setSeenDebounced] = useState(debouncedInput);
  if (seenDebounced !== debouncedInput) {
    setSeenDebounced(debouncedInput);
    const settling = debouncedInput.trim();
    setTerm(settling);
    if (settling === "") setWho("anyone");
  }

  // Replaced, not pushed: each settled term is the same search refined, not a page to go back to.
  // The filter rides along so a deep link and a reload land on the same result.
  const whoParam = who === "anyone" ? null : who;
  useEffect(() => {
    const settled =
      (params.get("term") ?? "") === term && params.get(WHO) === whoParam;
    if (settled) return;
    const next = new URLSearchParams(params);
    write(next, "term", term === "" ? null : term);
    write(next, WHO, whoParam);
    setParams(next, { replace: true });
  }, [term, whoParam, params, setParams]);

  const enabled = term !== "";
  // Everyone is asked for once and narrowed here, so each narrowing can show its count before it is chosen.
  const result = useExpertiseSearch(slug, { term, demonstratedOnly: false });
  const counts =
    result.data === undefined ? undefined : countsByWho(result.data);

  // The filter belongs to the question on screen, so every new question drops it: a picked area
  // lists as many people as the count on the tag just tapped, and clearing leaves an idle page
  // with nothing in the URL. The debounce path above drops it the same way when the box empties.
  // Editing a term that stays non-empty keeps it, so a reader mid-typing is not fought.
  function askAbout(tag: string, narrowing: Who = "anyone") {
    setInput(tag);
    setTerm(tag);
    setWho(narrowing);
  }

  return (
    <section className="flex flex-col gap-6">
      {/* One panel: the search controls and the persistent topic map. */}
      <div className="border-border bg-surface overflow-hidden rounded-2xl border shadow-sm">
        <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
          <SearchField
            label="Who knows…?"
            value={input}
            onChange={setInput}
            placeholder="Who knows…?"
            className="flex-1"
          />
        </div>
        {nothingTyped(input, term) && (
          <OpenDoorsShelf slug={slug} onOpen={(tag) => askAbout(tag, "open")} />
        )}
        <div className="border-border border-t p-5">
          <CommunityKnows
            slug={slug}
            onSelect={(tag) => askAbout(tag)}
            activeTag={term}
          />
        </div>
      </div>

      {enabled && (
        <div>
          <div className="mb-3 flex flex-wrap items-center gap-3">
            <h2 className="text-foreground font-semibold">
              Who knows <em>{term}</em>
            </h2>
            <button
              type="button"
              onClick={() => askAbout("")}
              className="text-primary-strong hover:text-primary cursor-pointer text-sm font-medium"
            >
              clear
            </button>
            <SegmentedControl
              label="Who to list"
              hideLabel
              value={who}
              options={whoOptions(counts)}
              onChange={(value) => setWho(parseWho(value))}
              className="sm:ml-auto"
            />
          </div>
          <PractitionerList
            practitioners={filterByWho(result.data ?? [], who)}
            isLoading={result.isLoading}
            isError={result.isError}
            term={term}
            who={who}
            onShowAnyone={() => setWho("anyone")}
          />
          <LearnersBridge slug={slug} term={term} />
        </div>
      )}
    </section>
  );
}
