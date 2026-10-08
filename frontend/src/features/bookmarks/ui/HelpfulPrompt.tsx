import { ThumbsUp } from "lucide-react";

import { Button } from "@/components/ui/Button";
import { useMarkHelpful } from "@/features/bookmarks/api/use-save";
import { cn } from "@/lib/cn";

/** A single affirmative toggle — shown only in the personal library, never on
 * the artifact itself. Mark a save as helpful, or click again to clear it back
 * to neutral. There is no "not helpful": silence already says that. */
export function HelpfulPrompt({
  artifactId,
  helped,
}: Readonly<{ artifactId: number; helped: boolean | null }>) {
  const mark = useMarkHelpful(artifactId);
  const isHelpful = helped === true;

  return (
    <div className="flex items-center gap-2">
      <span
        className={cn(
          "mr-auto text-sm font-semibold",
          isHelpful ? "text-foreground" : "text-muted-foreground",
        )}
      >
        {isHelpful ? "Marked helpful" : "Was this helpful?"}
      </span>
      <Button
        variant={isHelpful ? "secondary" : "outline"}
        size="sm"
        className={cn("gap-1.5", isHelpful && "text-success")}
        aria-pressed={isHelpful}
        isDisabled={mark.isPending}
        onPress={() => mark.mutate(isHelpful ? null : true)}
      >
        <ThumbsUp className={cn("size-4", isHelpful && "fill-current")} />
        Helpful
      </Button>
    </div>
  );
}
