import { Heart } from "lucide-react";

/** How many people loved a thing. Content is what gets loved, never a member. */
export function LoveCount({ count }: Readonly<{ count: number }>) {
  return (
    <span className="text-primary inline-flex shrink-0 items-center gap-1 text-xs font-semibold">
      <Heart size={12} className="fill-primary" />
      {count}
    </span>
  );
}
