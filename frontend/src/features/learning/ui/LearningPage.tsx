import { useParams, useSearchParams } from "react-router-dom";

import { useLearningPage } from "@/features/learning/api/use-learning";
import { WantedTable } from "@/features/learning/ui/WantedTable";
import { WantToLearnPanel } from "@/features/learning/ui/WantToLearnPanel";
import { normalizeTag } from "@/lib/normalize-tag";

/** The area in its stored form, so a hand-typed `?area=Kubernetes` reads as the row it names, and a blank one picks nothing. */
function pickedArea(raw: string | null): string | null {
  const tag = raw === null ? "" : normalizeTag(raw);
  return tag === "" ? null : tag;
}

/** The member's own areas to learn, then what the community wants to learn against who can be asked. */
export function LearningPage() {
  const { slug = "" } = useParams();
  const [params, setParams] = useSearchParams();
  const page = useLearningPage(slug);
  const area = pickedArea(params.get("area"));

  function pick(tag: string) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set("area", tag);
        return next;
      },
      { replace: true },
    );
  }

  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-foreground font-display text-xl font-semibold">
        Learning
      </h1>
      {page.isLoading && (
        <p className="text-muted-foreground text-sm">Loading…</p>
      )}
      {page.isError && (
        <p role="alert" className="text-danger text-sm">
          Could not load learning.
        </p>
      )}
      {page.data !== undefined && (
        <>
          {page.data.member && (
            <WantToLearnPanel slug={slug} page={page.data} />
          )}
          <WantedTable
            slug={slug}
            wanted={page.data.wanted}
            activeTag={area}
            onPick={pick}
          />
        </>
      )}
    </section>
  );
}
