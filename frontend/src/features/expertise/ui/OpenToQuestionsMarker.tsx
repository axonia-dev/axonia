import { X } from "lucide-react";
import { Button } from "react-aria-components";

import { iconButtonClass } from "@/components/ui/IconButton";
import { TierBadge } from "@/features/expertise/ui/TierBadge";
import { cn } from "@/lib/cn";

interface Props {
  tag: string;
  onRemove?: ((tag: string) => void) | undefined;
  isRemoving?: boolean | undefined;
}

export function OpenToQuestionsMarker({
  tag,
  onRemove,
  isRemoving = false,
}: Readonly<Props>) {
  return (
    <span className="mt-1 inline-flex items-center gap-1">
      <TierBadge tier="OPEN_TO_QUESTIONS" />
      {onRemove && (
        <Button
          aria-label={`Stop being open to questions on ${tag}`}
          isPending={isRemoving}
          onPress={() => onRemove(tag)}
          className={cn(iconButtonClass("danger"), "size-6 [&_svg]:size-3.5")}
        >
          <X aria-hidden />
        </Button>
      )}
    </span>
  );
}
