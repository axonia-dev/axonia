import { useState } from "react";

import { useAreaEvidence } from "@/features/expertise/api/use-area-evidence";
import type { EvidenceItem } from "@/features/expertise/domain/expertise";
import { EvidenceRow } from "@/features/expertise/ui/evidence-row";

interface PagerButtonProps {
  open: boolean;
  hasNextPage: boolean;
  remaining: number;
  onShowOlder: () => void;
  onShowMore: () => void;
}

// The pager's single control: "Show older proof" before it's opened, "Show
// more" while another page remains, nothing once exhausted.
function PagerButton({
  open,
  hasNextPage,
  remaining,
  onShowOlder,
  onShowMore,
}: Readonly<PagerButtonProps>) {
  if (!open) {
    return (
      <button
        type="button"
        onClick={onShowOlder}
        className="text-primary text-xs font-semibold"
      >
        Show older proof ({remaining} more)
      </button>
    );
  }
  if (!hasNextPage) return null;
  return (
    <button
      type="button"
      onClick={onShowMore}
      className="text-primary text-xs font-semibold"
    >
      Show more
    </button>
  );
}

interface OlderProofProps {
  tag: string;
  slug: string | undefined;
  startOffset: number;
  remaining: number;
  onDismiss: ((item: EvidenceItem) => void) | undefined;
}

// Pages an area's remaining evidence on demand. Disabled (no fetch) until the
// member opts in by clicking "Show older proof".
function OlderProof({
  tag,
  slug,
  startOffset,
  remaining,
  onDismiss,
}: Readonly<OlderProofProps>) {
  const [open, setOpen] = useState(false);
  const q = useAreaEvidence(tag, startOffset, open, slug);
  const older = (q.data?.pages ?? []).flatMap((page) => page.items);
  return (
    <>
      {older.map((item, idx) => (
        <EvidenceRow
          key={`older-${item.sourceType}-${item.sourceId}-${idx}`}
          item={item}
          onDismiss={onDismiss}
        />
      ))}
      <li className="py-1.5">
        <PagerButton
          open={open}
          hasNextPage={q.hasNextPage}
          remaining={remaining}
          onShowOlder={() => setOpen(true)}
          onShowMore={() => {
            void q.fetchNextPage();
          }}
        />
      </li>
    </>
  );
}

interface Props {
  items: EvidenceItem[];
  onDismiss?: (item: EvidenceItem) => void;
  tag?: string;
  totalCount?: number;
  /** The community page the evidence is read on; older proof then pages that community alone. */
  slug?: string | undefined;
}

export function EvidenceDetails({
  items,
  onDismiss,
  tag,
  totalCount,
  slug,
}: Readonly<Props>) {
  const hasOlderProof =
    tag !== undefined && totalCount !== undefined && totalCount > items.length;

  if (items.length === 0 && !hasOlderProof) {
    return (
      <p className="text-muted-foreground text-sm">
        No recent evidence recorded.
      </p>
    );
  }
  return (
    <ul className="divide-border divide-y">
      {items.map((item, idx) => (
        <EvidenceRow
          key={`${item.sourceType}-${item.sourceId}-${idx}`}
          item={item}
          onDismiss={onDismiss}
        />
      ))}
      {tag !== undefined &&
        totalCount !== undefined &&
        totalCount > items.length && (
          <OlderProof
            tag={tag}
            slug={slug}
            startOffset={items.length}
            remaining={totalCount - items.length}
            onDismiss={onDismiss}
          />
        )}
    </ul>
  );
}
