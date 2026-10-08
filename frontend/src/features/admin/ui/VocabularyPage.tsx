import type { ReactNode } from "react";
import { Navigate, useParams } from "react-router-dom";

import { CountBadge } from "@/components/patterns/CountBadge";
import { PageHeader } from "@/components/patterns/PageHeader";
import { SegmentedNav } from "@/components/ui/SegmentedNav";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { PermissionsLoadFailed } from "@/features/admin/ui/RequirePermission";
import { VOCABULARY_PATH } from "@/features/admin/ui/vocabulary";
import { VOCABULARY_TABS } from "@/features/admin/ui/vocabulary-tabs";
import { usePendingReactionCount } from "@/features/recognition/api/use-reactions";

/** Reactions is the one catalogue that fills from outside: a member proposes, and the queue
 *  grows with nothing on the page to say so. The badge is absent rather than zero, so an
 *  empty queue adds nothing to read. */
function tabBadge(path: string, waiting: number): ReactNode {
  if (path !== "reactions" || waiting === 0) {
    return undefined;
  }
  return <CountBadge value={waiting} label="waiting for approval" />;
}

/**
 * The organization's three classification catalogues on one page, one tab each.
 *
 * The heading never moves: a rail item and the page it opens disagreeing is the fault
 * this page exists to fix, so a tab changes the body and nothing else.
 *
 * Each tab is a real URL rather than local state, which is what lets the redirect table
 * point straight at one and lets the back button work between them.
 */
export function VocabularyPage({ base }: Readonly<{ base: string }>) {
  const { tab } = useParams();
  const { can, isLoading, isError, retry } = usePermissions();
  const waitingReactions = usePendingReactionCount(can("reaction:read"));
  if (isLoading) return null;
  // An unread answer is not a refusal: redirecting would drop the tab the member asked for.
  if (isError) {
    return <PermissionsLoadFailed title="Vocabulary" read={retry} />;
  }

  const permitted = VOCABULARY_TABS.filter((entry) => can(entry.permission));
  const fallback = permitted[0];
  // Nothing to show and nothing to redirect to: the rail hides this item entirely for
  // such a caller, so this only catches a hand-typed or bookmarked URL.
  if (fallback === undefined) return <Navigate to={base} replace />;

  const current = permitted.find((entry) => entry.path === tab);
  // Covers both the index (no `:tab` at all) and a tab this caller may not read.
  if (current === undefined) {
    return (
      <Navigate to={`${base}/${VOCABULARY_PATH}/${fallback.path}`} replace />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Vocabulary" />
      {permitted.length > 1 && (
        <SegmentedNav
          label="Vocabulary catalogues"
          items={permitted.map((entry) => ({
            to: `${base}/${VOCABULARY_PATH}/${entry.path}`,
            label: entry.label,
            icon: <entry.icon size={14} />,
            badge: tabBadge(entry.path, waitingReactions),
          }))}
        />
      )}
      <current.Manager />
    </div>
  );
}
