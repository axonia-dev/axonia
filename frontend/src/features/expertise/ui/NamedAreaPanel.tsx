import { Plus } from "lucide-react";
import { useState } from "react";

import { ConfirmActionDialog } from "@/components/patterns/ConfirmActionDialog";
import { EmptyState } from "@/components/patterns/EmptyState";
import { Button } from "@/components/ui/Button";
import { useExpertiseMap } from "@/features/expertise/api/use-expertise-map";
import {
  useNamedAreaActions,
  useNamedAreas,
} from "@/features/expertise/api/use-named-areas";
import type { TopicMap } from "@/features/expertise/domain/expertise";
import type { NamedArea } from "@/features/expertise/domain/named-area";
import {
  type DrawnGroup,
  groupsFor,
} from "@/features/expertise/ui/named-area-grouping";
import { NamedAreaCard } from "@/features/expertise/ui/NamedAreaCard";
import {
  NamedAreaNameDialog,
  type NameDraft,
} from "@/features/expertise/ui/NamedAreaNameDialog";

interface Props {
  slug: string;
  /** The community's canonical tags. */
  vocabulary: string[];
}

interface Naming {
  draft: NameDraft;
  renaming: NamedArea | null;
}

function creating(tags: string[]): Naming {
  const draft = { title: "New named area", confirmLabel: "Create", name: "" };
  return { draft: { ...draft, tags }, renaming: null };
}

function renamingOf(namedArea: NamedArea): Naming {
  const draft = { title: `Rename ${namedArea.name}`, confirmLabel: "Rename" };
  return {
    draft: { ...draft, name: namedArea.name, tags: [] },
    renaming: namedArea,
  };
}

function detected(map: TopicMap | null, namedAreas: NamedArea[]): DrawnGroup[] {
  if (map === null) return [];
  return groupsFor(map.topics, map.links, namedAreas).filter(
    (group) => group.kind === "neighbourhood",
  );
}

interface EmptyProps {
  neighbourhoods: DrawnGroup[];
  onStart: (tags: string[]) => void;
}

/** The graph's grouping is right often enough to be a first draft. */
function NoNamedAreas({ neighbourhoods, onStart }: Readonly<EmptyProps>) {
  const starts = neighbourhoods.map((group) => (
    <Button
      key={group.anchor}
      variant="outline"
      size="sm"
      onPress={() => onStart(group.tags)}
    >
      {`Start from the ${group.anchor} neighbourhood`}
    </Button>
  ));
  return (
    <EmptyState
      title="No named areas"
      description="Tags in no named area are grouped automatically."
      {...(starts.length > 0
        ? {
            action: (
              <div className="flex flex-wrap justify-center gap-2">
                {starts}
              </div>
            ),
          }
        : {})}
    />
  );
}

interface ListProps {
  query: ReturnType<typeof useNamedAreas>;
  map: TopicMap | null;
  vocabulary: string[];
  actions: ReturnType<typeof useNamedAreaActions>;
  onStart: (tags: string[]) => void;
  onRename: (namedArea: NamedArea) => void;
  onDelete: (namedArea: NamedArea) => void;
}

function NamedAreaList({
  query,
  map,
  vocabulary,
  actions,
  onStart,
  onRename,
  onDelete,
}: Readonly<ListProps>) {
  const namedAreas = query.data;
  if (namedAreas === undefined) {
    return query.isError ? (
      <p role="alert" className="text-danger text-sm">
        Could not load named areas.
      </p>
    ) : null;
  }
  if (namedAreas.length === 0) {
    return (
      <NoNamedAreas
        neighbourhoods={detected(map, namedAreas)}
        onStart={onStart}
      />
    );
  }
  const onMap = map && new Set(map.topics.map((topic) => topic.canonicalTag));
  return (
    <div className="flex flex-col gap-2">
      {namedAreas.map((namedArea) => (
        <NamedAreaCard
          key={namedArea.id}
          namedArea={namedArea}
          namedAreas={namedAreas}
          vocabulary={vocabulary}
          onMap={onMap}
          onRename={onRename}
          onDelete={onDelete}
          onPlace={actions.placeInNamedArea}
          onTakeOut={actions.removeFromNamedArea}
        />
      ))}
    </div>
  );
}

export function NamedAreaPanel({ slug, vocabulary }: Readonly<Props>) {
  const namedAreasQuery = useNamedAreas(slug);
  const mapQuery = useExpertiseMap(slug);
  const actions = useNamedAreaActions(slug);
  const [naming, setNaming] = useState<Naming | null>(null);
  const [deleting, setDeleting] = useState<NamedArea | null>(null);

  const map = mapQuery.isSuccess ? mapQuery.data : null;

  function confirmName(name: string) {
    if (naming?.renaming) actions.renameNamedArea(naming.renaming.id, name);
    else actions.createNamedArea(name, naming?.draft.tags);
    setNaming(null);
  }

  return (
    <div>
      <div className="mb-3 flex items-center justify-between gap-2">
        <h2 className="text-foreground font-semibold">Named areas</h2>
        <Button
          variant="secondary"
          size="sm"
          onPress={() => setNaming(creating([]))}
        >
          <Plus aria-hidden className="size-3.5" />
          New named area
        </Button>
      </div>
      <NamedAreaList
        query={namedAreasQuery}
        map={map}
        vocabulary={vocabulary}
        actions={actions}
        onStart={(tags) => setNaming(creating(tags))}
        onRename={(target) => setNaming(renamingOf(target))}
        onDelete={setDeleting}
      />
      <NamedAreaNameDialog
        draft={naming?.draft ?? null}
        onCancel={() => setNaming(null)}
        onConfirm={confirmName}
      />
      <ConfirmActionDialog
        isOpen={deleting !== null}
        title={`Delete ${deleting?.name ?? ""}?`}
        body="Its tags go back to being grouped automatically."
        error={null}
        confirmLabel="Delete"
        isPending={false}
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting !== null) actions.deleteNamedArea(deleting);
          setDeleting(null);
        }}
      />
    </div>
  );
}
