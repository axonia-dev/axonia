import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import {
  useAttachResource,
  useSessionResources,
} from "@/features/knowledge/api/use-session-resources";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import type {
  AttachResourceInput,
  SessionResource,
} from "@/features/knowledge/domain/session-resource";
import { CaptureArtifactForm } from "@/features/knowledge/ui/CaptureArtifactForm";
import { LibrarySearchPanel } from "@/features/knowledge/ui/LibrarySearchPanel";

interface Props {
  slug: string;
  sessionId: number;
  segmentId?: number;
  defaultTitle?: string;
  defaultUrl?: string;
  provenanceLabel?: string;
  /**
   * The presenters of the slot this button hangs under, when the caller may credit one of them.
   * Threaded straight to the capture form, which decides whose contribution the new artifact is.
   * Absent at session level: nobody presented "the session", so the caller is the contributor.
   */
  presenterUserIds?: string[];
}

/**
 * A resource hangs off either one slot or the session itself, so "already attached" is only ever
 * true of the place this button sits: a sibling slot's copy of the artifact is not this slot's.
 */
function artifactsOn(
  resources: SessionResource[] | undefined,
  segmentId: number | undefined,
): number[] {
  return (resources ?? [])
    .filter((resource) => (resource.segmentId ?? undefined) === segmentId)
    .map((resource) => resource.artifact.id);
}

function inFlightArtifactId(attach: {
  isPending: boolean;
  variables: AttachResourceInput | undefined;
}): number | null {
  if (!attach.isPending || attach.variables === undefined) {
    return null;
  }
  return attach.variables.artifactId;
}

export function AddResourceButton({
  slug,
  sessionId,
  segmentId,
  defaultTitle,
  defaultUrl,
  provenanceLabel,
  presenterUserIds = [],
}: Readonly<Props>) {
  const [open, setOpen] = useState(false);
  const [view, setView] = useState<"search" | "create">("search");
  const [query, setQuery] = useState("");
  const attach = useAttachResource(slug, sessionId);
  const resources = useSessionResources(slug, sessionId);

  const attachedArtifactIds = artifactsOn(resources.data, segmentId);
  const attachingArtifactId = inFlightArtifactId(attach);

  function close() {
    reset();
    setOpen(false);
  }

  function reset() {
    setView("search");
    setQuery("");
  }

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      reset();
    }
  }

  function attachArtifact(artifactId: number, onDone?: () => void) {
    attach.mutate(
      { ...(segmentId !== undefined ? { segmentId } : {}), artifactId },
      { onSuccess: () => onDone?.() },
    );
  }

  const formDefaultTitle = query.trim() || defaultTitle || "";
  const defaultUrlProps = defaultUrl !== undefined ? { defaultUrl } : {};

  return (
    <>
      {/* An inline text action, not a chunky button: it carries no padding of its own, so it sits
       * flush with the description and tags above it, and answers a hover by deepening its colour
       * rather than by painting a fill that would bleed into the card's edge. */}
      <Button
        variant="ghost"
        size="sm"
        className="hover:text-primary-strong w-fit gap-1 px-0 hover:bg-transparent"
        onPress={() => setOpen(true)}
      >
        ＋ Add resource
      </Button>

      <Dialog
        isOpen={open}
        onOpenChange={handleOpenChange}
        title="Add a resource"
      >
        {view === "search" ? (
          <LibrarySearchPanel
            slug={slug}
            query={query}
            attachedArtifactIds={attachedArtifactIds}
            attachingArtifactId={attachingArtifactId}
            onQueryChange={setQuery}
            onAttach={attachArtifact}
            onCreate={() => setView("create")}
          />
        ) : (
          <CaptureArtifactForm
            slug={slug}
            defaultTitle={formDefaultTitle}
            {...defaultUrlProps}
            presenterUserIds={presenterUserIds}
            submitLabel="Attach"
            onBack={() => setView("search")}
            provenance={{
              sessionId,
              ...(segmentId !== undefined ? { segmentId } : {}),
              ...(provenanceLabel !== undefined
                ? { label: provenanceLabel }
                : {}),
            }}
            onCaptured={(artifact: Artifact) =>
              attachArtifact(artifact.id, close)
            }
          />
        )}
      </Dialog>
    </>
  );
}
