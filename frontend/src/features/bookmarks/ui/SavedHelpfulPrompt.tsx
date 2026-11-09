import { useMySaved } from "@/features/bookmarks/api/use-save";
import { HelpfulPrompt } from "@/features/bookmarks/ui/HelpfulPrompt";

function helpedOf(
  items: ReturnType<typeof useMySaved>["data"],
  artifactId: number,
): boolean | null {
  const item = items?.find(
    (saved) => saved.kind === "artifact" && saved.artifact.id === artifactId,
  );
  return item?.kind === "artifact" ? item.helped : null;
}

/** The "did this help?" answer for a saved artifact shown outside the saved list itself. */
export function SavedHelpfulPrompt({
  artifactId,
}: Readonly<{ artifactId: number }>) {
  const saved = useMySaved();
  return (
    <HelpfulPrompt
      artifactId={artifactId}
      helped={helpedOf(saved.data, artifactId)}
    />
  );
}
