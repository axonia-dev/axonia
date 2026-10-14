import { ECHO } from "@/features/expertise/ui/echo-timeline";
import { MatchedAreasLine } from "@/features/expertise/ui/MatchedAreasLine";
import { WhoKnowsSection } from "@/features/expertise/ui/WhoKnowsSection";

/** What a settled term answers with: who knows the one area it names, or a choice among the several it names. */
export function TermAnswer({
  slug,
  matchedAreas,
  onSeeWhoKnows,
  onChooseMatchedArea,
  echoing,
}: Readonly<{
  slug: string;
  matchedAreas: readonly string[];
  onSeeWhoKnows: (tag: string) => void;
  onChooseMatchedArea: (tag: string) => void;
  /** True while the map plays an echo for the term, which the people listed wait for. */
  echoing: boolean;
}>) {
  if (matchedAreas.length === 1) {
    return (
      <WhoKnowsSection
        key={matchedAreas[0]}
        slug={slug}
        tag={matchedAreas[0]!}
        onOpenSearch={onSeeWhoKnows}
        cueMs={echoing ? ECHO.panelCueMs : 0}
      />
    );
  }
  if (matchedAreas.length > 1) {
    return (
      <MatchedAreasLine tags={matchedAreas} onChoose={onChooseMatchedArea} />
    );
  }
  return null;
}
