import {
  type DragEndEvent,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { Sparkles } from "lucide-react";
import { useState } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import {
  useCurationSuggestions,
  useCurationVocabulary,
} from "@/features/expertise/api/use-curation";
import { useTagDismissal } from "@/features/expertise/api/use-tag-dismissal";
import { useTagLifecycle } from "@/features/expertise/api/use-tag-lifecycle";
import { useTagMerge } from "@/features/expertise/api/use-tag-merge";
import type { ExpertiseArea } from "@/features/expertise/domain/expertise";
import { BatchMergeBar } from "@/features/expertise/ui/BatchMergeBar";
import { DeleteTagDialog } from "@/features/expertise/ui/DeleteTagDialog";
import {
  defaultSurvivor,
  resolveMerge,
} from "@/features/expertise/ui/merge-helpers";
import { MergeSuggestionCard } from "@/features/expertise/ui/MergeSuggestionCard";
import { NamedAreaPanel } from "@/features/expertise/ui/NamedAreaPanel";
import { RenameTagDialog } from "@/features/expertise/ui/RenameTagDialog";
import { TagMergeGrid } from "@/features/expertise/ui/TagMergeGrid";
import { useTagSelection } from "@/features/expertise/ui/use-tag-selection";

interface Props {
  slug: string;
}

function CurationHeader() {
  return (
    <header className="flex flex-col gap-1">
      <h1 className="text-foreground font-display text-xl font-semibold">
        Curation
      </h1>
      <p className="text-muted-foreground text-sm">
        Merge, rename and delete the tags this community uses, and group them
        into named areas.
      </p>
    </header>
  );
}

export function TagCurationView({ slug }: Readonly<Props>) {
  const suggestionsQuery = useCurationSuggestions(slug);
  const vocabularyQuery = useCurationVocabulary(slug);
  const { mergeInto, splitOff } = useTagMerge(slug);
  const { dismissPair } = useTagDismissal(slug);
  const { renameTag, deleteTag } = useTagLifecycle(slug);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<string | null>(null);
  const {
    selected,
    selectedList,
    survivorOverride,
    toggleSelect,
    setSurvivorOverride,
    clearSelection,
  } = useTagSelection();
  // A distance constraint lets a plain click reach the checkbox / split-×
  // buttons inside each card; only a pointer move of ≥8px starts a drag.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
  );

  function handleDragEnd(event: DragEndEvent) {
    const move = resolveMerge(
      String(event.active.id),
      event.over ? String(event.over.id) : null,
    );
    if (move) mergeInto([move.variant], move.canonical);
  }

  if (suggestionsQuery.isLoading || vocabularyQuery.isLoading) {
    return (
      <section className="flex flex-col gap-4">
        <CurationHeader />
        <p className="text-muted-foreground text-center text-sm">Loading…</p>
      </section>
    );
  }

  const suggestions = suggestionsQuery.data ?? [];
  const vocabulary = vocabularyQuery.data ?? [];

  const survivor =
    survivorOverride !== null && selected.has(survivorOverride)
      ? survivorOverride
      : (defaultSurvivor(selectedList, vocabulary) ?? "");

  function runBatchMerge() {
    const variants = selectedList.filter((t) => t !== survivor);
    if (variants.length) mergeInto(variants, survivor);
    clearSelection();
  }

  return (
    <section className="flex flex-col gap-4">
      <CurationHeader />
      <div className="flex flex-col gap-8">
        <SuggestionsSection
          suggestions={suggestions}
          onMerge={(variant, canonical) => mergeInto([variant], canonical)}
          onDismiss={(tagA, tagB) => dismissPair(tagA, tagB)}
        />
        <NamedAreaPanel
          slug={slug}
          vocabulary={vocabulary.map((area) => area.canonicalTag)}
        />
        <AllTagsSection
          vocabulary={vocabulary}
          selected={selected}
          selectedList={selectedList}
          survivor={survivor}
          onToggleSelect={toggleSelect}
          onSurvivorChange={setSurvivorOverride}
          onMerge={runBatchMerge}
          onClear={clearSelection}
          onSplit={splitOff}
          onDragEnd={handleDragEnd}
          sensors={sensors}
          onRename={setRenaming}
          onDelete={setDeleting}
        />
      </div>
      <RenameTagDialog
        tag={renaming}
        existingTags={vocabulary.map((area) => area.canonicalTag)}
        onCancel={() => setRenaming(null)}
        onConfirm={(to) => {
          if (renaming !== null) renameTag(renaming, to);
          setRenaming(null);
        }}
      />
      <DeleteTagDialog
        slug={slug}
        tag={deleting}
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting !== null) deleteTag(deleting);
          setDeleting(null);
        }}
      />
    </section>
  );
}

interface SuggestionsSectionProps {
  suggestions: { tagA: string; tagB: string; score: number }[];
  onMerge: (variant: string, canonical: string) => void;
  onDismiss: (tagA: string, tagB: string) => void;
}

function SuggestionsSection({
  suggestions,
  onMerge,
  onDismiss,
}: Readonly<SuggestionsSectionProps>) {
  return (
    <div>
      <h2 className="text-foreground mb-3 font-semibold">Needs attention</h2>
      {suggestions.length === 0 ? (
        <div className="border-border bg-surface-sunken flex items-center gap-3 rounded-xl border p-4">
          <Sparkles aria-hidden className="text-muted-foreground size-5" />
          <div>
            <p className="text-foreground text-sm font-medium">
              No duplicate tags to review
            </p>
            <p className="text-muted-foreground text-xs">
              Suggestions appear here when two tags look like the same thing.
            </p>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {suggestions.map((s) => (
            <MergeSuggestionCard
              key={`${s.tagA}-${s.tagB}`}
              suggestion={s}
              onMerge={({ variant, canonical }) => onMerge(variant, canonical)}
              onDismiss={({ tagA, tagB }) => onDismiss(tagA, tagB)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

interface AllTagsSectionProps {
  vocabulary: ExpertiseArea[];
  selected: Set<string>;
  selectedList: string[];
  survivor: string;
  onToggleSelect: (tag: string) => void;
  onSurvivorChange: (tag: string) => void;
  onMerge: () => void;
  onClear: () => void;
  onSplit: (variant: string, canonical: string) => void;
  onDragEnd: (event: DragEndEvent) => void;
  sensors: ReturnType<typeof useSensors>;
  onRename: (tag: string) => void;
  onDelete: (tag: string) => void;
}

function AllTagsSection({
  vocabulary,
  selected,
  selectedList,
  survivor,
  onToggleSelect,
  onSurvivorChange,
  onMerge,
  onClear,
  onSplit,
  onDragEnd,
  sensors,
  onRename,
  onDelete,
}: Readonly<AllTagsSectionProps>) {
  return (
    <div>
      <h2 className="text-foreground mb-3 font-semibold">
        All tags{" "}
        <span className="text-muted-foreground font-normal">
          · {vocabulary.length}
        </span>
      </h2>
      {selected.size >= 2 && (
        <div className="mb-3">
          <BatchMergeBar
            selected={selectedList}
            survivor={survivor}
            onSurvivorChange={onSurvivorChange}
            onMerge={onMerge}
            onClear={onClear}
          />
        </div>
      )}
      {vocabulary.length === 0 ? (
        <EmptyState
          title="No tags yet"
          description="Tags appear here as members share their expertise."
        />
      ) : (
        <TagMergeGrid
          areas={vocabulary}
          selected={selected}
          survivor={survivor}
          onToggleSelect={onToggleSelect}
          onSplit={onSplit}
          onDragEnd={onDragEnd}
          sensors={sensors}
          onRename={onRename}
          onDelete={onDelete}
        />
      )}
    </div>
  );
}
