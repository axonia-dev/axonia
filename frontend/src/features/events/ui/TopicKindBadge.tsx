import type { TopicKind } from "@/features/events/domain/topic";
import { cn } from "@/lib/cn";

const KIND_BADGE: Record<TopicKind, { label: string; tone: string }> = {
  REQUEST: { label: "Request", tone: "bg-secondary/10 text-secondary" },
  OFFER: { label: "Offer", tone: "bg-success/10 text-success" },
};

export function TopicKindBadge({ kind }: Readonly<{ kind: TopicKind }>) {
  const { label, tone } = KIND_BADGE[kind];
  return (
    <span
      className={cn(
        "rounded px-1.5 py-0.5 text-[10px] font-semibold tracking-wide uppercase",
        tone,
      )}
    >
      {label}
    </span>
  );
}
