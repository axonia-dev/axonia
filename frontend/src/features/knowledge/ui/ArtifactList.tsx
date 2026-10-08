import type { ReactNode } from "react";
import { useEffect, useRef } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import { SaveAction } from "@/features/bookmarks/ui/SaveAction";
import { useSeriesList } from "@/features/events/api/use-series";
import { useSessions } from "@/features/events/api/use-sessions";
import { useUsers } from "@/features/identity/api/use-users";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import type { CardContributor } from "@/features/knowledge/ui/ArtifactCard";
import { ArtifactCard } from "@/features/knowledge/ui/ArtifactCard";
import { provenanceText } from "@/features/knowledge/ui/provenance-text";
import { cn } from "@/lib/cn";
import { userHue } from "@/lib/user-hue";

function EmptyLibrary({
  isFiltered,
}: Readonly<{ isFiltered: boolean | undefined }>) {
  return isFiltered ? (
    <EmptyState
      title="No matching artifacts"
      description="Change the search or filters."
    />
  ) : (
    <EmptyState
      title="Nothing in the library yet"
      description="Capture a talk, recording, RFC, or write-up to start the collection."
    />
  );
}

interface Props {
  slug: string;
  artifacts: Artifact[];
  isLoading: boolean;
  isError: boolean;
  /** A search or filter is narrowing the list, so an empty one means no match, not an empty library. */
  isFiltered?: boolean;
  onTagClick?: (tag: string) => void;
  renderArtifactActions?: (artifact: Artifact) => ReactNode;
  renderArtifactReactions?: (artifact: Artifact) => ReactNode;
  renderArtifactManageActions?: (artifact: Artifact) => ReactNode;
  /** When set, that artifact's row is ringed and scrolled into view (deep-link landing). */
  focusedId?: number | undefined;
}

export function ArtifactList({
  slug,
  artifacts,
  isLoading,
  isError,
  isFiltered,
  onTagClick,
  renderArtifactActions,
  renderArtifactReactions,
  renderArtifactManageActions,
  focusedId,
}: Readonly<Props>) {
  // Resolve contributors and session provenance once for the whole page, rather
  // than fetching per card. Hooks run before the early returns below.
  const userIds = [
    ...new Set(artifacts.map((artifact) => artifact.contributorUserId)),
  ].sort((a, b) => a.localeCompare(b));
  const userById = new Map(Object.entries(useUsers(userIds).data ?? {}));
  const sessionById = new Map(
    (useSessions(slug).data ?? []).map((session) => [session.id, session]),
  );
  const seriesById = new Map(
    (useSeriesList(slug).data?.content ?? []).map((entry) => [
      entry.id,
      entry.title,
    ]),
  );
  const typeById = new Map(
    (useArtifactTypes().data ?? []).map((type) => [type.id, type]),
  );

  const focusRef = useRef<HTMLLIElement>(null);
  useEffect(() => {
    if (focusedId !== undefined && focusRef.current) {
      focusRef.current.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  }, [focusedId, artifacts]);

  function contributorFor(artifact: Artifact): CardContributor | undefined {
    const user = userById.get(artifact.contributorUserId);
    if (!user) {
      return undefined;
    }
    return {
      name: user.displayName,
      hue: userHue(user.id),
      avatarUrl: user.avatarUrl ?? null,
    };
  }

  function provenanceFor(artifact: Artifact): string | undefined {
    if (!artifact.provenance) {
      return undefined;
    }
    const session = sessionById.get(artifact.provenance.sessionId);
    const seriesTitle =
      session && session.seriesId !== null
        ? seriesById.get(session.seriesId)
        : undefined;
    return provenanceText(artifact.provenance.label, session, seriesTitle);
  }

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading…</p>;
  }
  if (isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load the library.
      </p>
    );
  }
  if (artifacts.length === 0) {
    return <EmptyLibrary isFiltered={isFiltered} />;
  }
  return (
    <ul className="flex flex-col gap-3">
      {artifacts.map((artifact) => {
        const contributor = contributorFor(artifact);
        const provenance = provenanceFor(artifact);
        const type = typeById.get(artifact.typeId);
        return (
          <li
            key={artifact.id}
            ref={artifact.id === focusedId ? focusRef : undefined}
            className={cn(
              artifact.id === focusedId &&
                "ring-primary ring-offset-background rounded-md ring-2 ring-offset-2 motion-safe:animate-[resource-in_0.35s_ease]",
            )}
          >
            <ArtifactCard
              slug={slug}
              artifact={artifact}
              {...(type
                ? { typeIcon: type.icon, typeAccent: type.accentColor }
                : {})}
              {...(contributor ? { contributor } : {})}
              {...(provenance !== undefined
                ? { provenanceText: provenance }
                : {})}
              {...(onTagClick ? { onTagClick } : {})}
              actions={
                <>
                  {renderArtifactActions?.(artifact)}
                  <SaveAction target={{ kind: "artifact", id: artifact.id }} />
                </>
              }
              {...(renderArtifactReactions
                ? { reactions: renderArtifactReactions(artifact) }
                : {})}
              {...(renderArtifactManageActions
                ? { manageActions: renderArtifactManageActions(artifact) }
                : {})}
            />
          </li>
        );
      })}
    </ul>
  );
}
