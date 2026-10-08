import type { ReactNode } from "react";
import { useState } from "react";

import { FilterToggleChip } from "@/components/patterns/FilterToggleChip";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import {
  useActionItems,
  useSettledActionItems,
} from "@/features/events/api/use-action-items";
import { useTopicCounts } from "@/features/events/api/use-topics";
import type { Topic, TopicKind } from "@/features/events/domain/topic";
import { ActionItemsTab } from "@/features/events/ui/ActionItemsTab";
import { BacklogPanel } from "@/features/events/ui/BacklogPanel";
import { PastTopicsPanel } from "@/features/events/ui/PastTopicsPanel";
import { ProposeTopicDialog } from "@/features/events/ui/ProposeTopicDialog";
import { ScheduledPanel } from "@/features/events/ui/ScheduledPanel";

type Tab = "backlog" | "scheduled" | "action-items" | "past";

const OPEN_ACTION_ITEMS = { outcome: "OPEN" } as const;

const KIND_FILTERS: ReadonlyArray<{
  id: TopicKind | undefined;
  label: string;
}> = [
  { id: undefined, label: "All" },
  { id: "REQUEST", label: "Requests" },
  { id: "OFFER", label: "Offers" },
];

function withCount(count: number | undefined): { count?: number } {
  return count === undefined ? {} : { count };
}

/** The Past tab holds settled action items beside done topics, so its badge counts both. Either
 *  half still unanswered leaves it off: a half-counted total reads as a smaller one. */
function withSum(
  a: number | undefined,
  b: number | undefined,
): { count?: number } {
  if (a === undefined || b === undefined) return {};
  return { count: a + b };
}

function BacklogTabContent({
  tab,
  slug,
  kind,
  onKind,
  renderTopicActions,
  renderScheduledActions,
}: Readonly<{
  tab: Tab;
  slug: string;
  kind: TopicKind | undefined;
  onKind: (v: TopicKind | undefined) => void;
  renderTopicActions?: (topic: Topic) => ReactNode;
  renderScheduledActions?: (topic: Topic) => ReactNode;
}>) {
  if (tab === "backlog") {
    return (
      <>
        <BacklogKindFilter kind={kind} onKind={onKind} />
        <BacklogPanel
          slug={slug}
          {...(kind ? { kind } : {})}
          {...(renderTopicActions ? { renderTopicActions } : {})}
        />
      </>
    );
  }
  if (tab === "scheduled") {
    return (
      <ScheduledPanel
        slug={slug}
        {...(renderScheduledActions ? { renderScheduledActions } : {})}
      />
    );
  }
  if (tab === "action-items") {
    return <ActionItemsTab slug={slug} />;
  }
  return <PastTopicsPanel slug={slug} />;
}

function BacklogKindFilter({
  kind,
  onKind,
}: Readonly<{
  kind: TopicKind | undefined;
  onKind: (v: TopicKind | undefined) => void;
}>) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {KIND_FILTERS.map((filter) => (
        <FilterToggleChip
          key={filter.label}
          active={kind === filter.id}
          onToggle={() => onKind(filter.id)}
        >
          {filter.label}
        </FilterToggleChip>
      ))}
    </div>
  );
}

export function BacklogView({
  slug,
  renderTopicActions,
  renderScheduledActions,
}: Readonly<{
  slug: string;
  renderTopicActions?: (topic: Topic) => ReactNode;
  renderScheduledActions?: (topic: Topic) => ReactNode;
}>) {
  const [tab, setTab] = useState<Tab>("backlog");
  const [kind, setKind] = useState<TopicKind | undefined>(undefined);

  const counts = useTopicCounts(slug).data;
  const actionItemCount = useActionItems(slug, OPEN_ACTION_ITEMS).data
    ?.totalElements;
  const settledCount = useSettledActionItems(slug).total;
  const tabs = [
    { id: "backlog", label: "Backlog", ...withCount(counts?.proposed) },
    { id: "scheduled", label: "Scheduled", ...withCount(counts?.scheduled) },
    {
      id: "action-items",
      label: "Action items",
      ...withCount(actionItemCount),
    },
    { id: "past", label: "Past", ...withSum(counts?.done, settledCount) },
  ];

  return (
    <section className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <SegmentedControl
          label="Topic view"
          hideLabel
          value={tab}
          options={tabs}
          onChange={(next) => setTab(next as Tab)}
        />
        <ProposeTopicDialog slug={slug} />
      </div>

      <BacklogTabContent
        tab={tab}
        slug={slug}
        kind={kind}
        onKind={setKind}
        {...(renderTopicActions ? { renderTopicActions } : {})}
        {...(renderScheduledActions ? { renderScheduledActions } : {})}
      />
    </section>
  );
}
