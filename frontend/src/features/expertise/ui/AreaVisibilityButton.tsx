import { Eye, EyeOff } from "lucide-react";

import { cn } from "@/lib/cn";

interface Props {
  tag: string;
  /** Names where the row sits, when several rows can carry the same tag. */
  community?: string | undefined;
  hidden: boolean;
  onPress: () => void;
}

/** The eye on an area row: pressed on a visible area it hides it, on a hidden one it shows it again. */
export function AreaVisibilityButton({
  tag,
  community,
  hidden,
  onPress,
}: Readonly<Props>) {
  const subject = community === undefined ? tag : `${tag} in ${community}`;
  return (
    <button
      type="button"
      aria-label={hidden ? `Show ${subject}` : `Hide ${subject}`}
      onClick={onPress}
      className={cn(
        "flex size-8 shrink-0 cursor-pointer items-center justify-center rounded-lg border transition-colors",
        hidden
          ? "text-primary border-[color-mix(in_srgb,var(--primary)_30%,var(--border))]"
          : "border-border text-muted-foreground hover:text-primary",
      )}
    >
      {hidden ? <EyeOff size={16} /> : <Eye size={16} />}
    </button>
  );
}
