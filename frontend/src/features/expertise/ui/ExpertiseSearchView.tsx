import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";

import { SearchField } from "@/components/ui/SearchField";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { useExpertiseSearch } from "@/features/expertise/api/use-expertise-search";
import { CommunityKnows } from "@/features/expertise/ui/CommunityKnows";
import { PractitionerList } from "@/features/expertise/ui/PractitionerList";
import { LearnersBridge } from "@/features/learning/ui/LearnersBridge";
import { useDebouncedValue } from "@/lib/use-debounced-value";

const DEBOUNCE_MS = 300;

const ANYONE = "anyone";
const DEMONSTRATED = "demonstrated";
const WHO = "who";

const PROOF_OPTIONS = [
  { id: ANYONE, label: "Anyone" },
  { id: DEMONSTRATED, label: "Practised" },
];

/** A null value leaves the parameter out, so a default never reaches the URL. */
function write(params: URLSearchParams, key: string, value: string | null) {
  if (value === null) {
    params.delete(key);
  } else {
    params.set(key, value);
  }
}

export function ExpertiseSearchView() {
  const { slug = "" } = useParams();
  const [params, setParams] = useSearchParams();
  const [input, setInput] = useState(params.get("term") ?? "");
  const [term, setTerm] = useState(() => input.trim());
  const [demonstratedOnly, setDemonstratedOnly] = useState(
    () => term !== "" && params.get(WHO) === DEMONSTRATED,
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
    if (settling === "") setDemonstratedOnly(false);
  }

  // Replaced, not pushed: each settled term is the same search refined, not a page to go back to.
  // The filter rides along so a deep link and a reload land on the same result.
  const who = demonstratedOnly ? DEMONSTRATED : null;
  useEffect(() => {
    const settled =
      (params.get("term") ?? "") === term && params.get(WHO) === who;
    if (settled) return;
    const next = new URLSearchParams(params);
    write(next, "term", term === "" ? null : term);
    write(next, WHO, who);
    setParams(next, { replace: true });
  }, [term, who, params, setParams]);

  const enabled = term !== "";
  const result = useExpertiseSearch(slug, { term, demonstratedOnly });

  // The filter belongs to the question on screen, so every new question drops it: a picked area
  // lists as many people as the count on the tag just tapped, and clearing leaves an idle page
  // with nothing in the URL. The debounce path above drops it the same way when the box empties.
  // Editing a term that stays non-empty keeps it, so a reader mid-typing is not fought.
  function askAbout(tag: string) {
    setInput(tag);
    setTerm(tag);
    setDemonstratedOnly(false);
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
        <div className="border-border border-t p-5">
          <CommunityKnows slug={slug} onSelect={askAbout} activeTag={term} />
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
              value={demonstratedOnly ? DEMONSTRATED : ANYONE}
              options={PROOF_OPTIONS}
              onChange={(value) => setDemonstratedOnly(value === DEMONSTRATED)}
              className="sm:ml-auto"
            />
          </div>
          <PractitionerList
            practitioners={result.data ?? []}
            isLoading={result.isLoading}
            isError={result.isError}
            demonstratedOnly={demonstratedOnly}
            onShowAnyone={() => setDemonstratedOnly(false)}
          />
          <LearnersBridge slug={slug} term={term} />
        </div>
      )}
    </section>
  );
}
