import { Bookmark } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import {
  useSave,
  useSaveState,
  useUnsave,
} from "@/features/bookmarks/api/use-save";
import type { SaveableRef } from "@/features/bookmarks/domain/saved";
import { cn } from "@/lib/cn";

/** Private bookmark gesture: no count, no label. Filled = saved. Aggregate
 * demand only ever resurfaces in community health, never here. */
export function SaveAction({ target }: Readonly<{ target: SaveableRef }>) {
  const navigate = useNavigate();
  const state = useSaveState(target);
  const save = useSave(target, () => {
    void navigate("/me/saved");
  });
  const unsave = useUnsave(target);

  const saved = state.data?.viewerHasSaved ?? false;

  return (
    <Button
      variant="ghost"
      size="sm"
      // 40×40 square so it lines up with the love button above it in the rail.
      // Violet (the secondary accent) keeps save visually distinct from the
      // pink love heart, in both light and dark themes. Saved reads from the
      // filled, violet glyph alone: no chip, no plate behind it.
      className={cn(
        "size-10 rounded-md p-0",
        saved ? "text-secondary" : "text-muted-foreground",
      )}
      aria-label={saved ? "Remove from saved" : "Save for later"}
      aria-pressed={saved}
      isDisabled={save.isPending || unsave.isPending}
      onPress={() => (saved ? unsave.mutate() : save.mutate())}
    >
      <Bookmark
        className={cn(
          "size-4 transition-transform",
          saved &&
            "fill-current motion-safe:animate-[heart-pop_280ms_var(--ease-standard,ease-out)]",
        )}
      />
    </Button>
  );
}
