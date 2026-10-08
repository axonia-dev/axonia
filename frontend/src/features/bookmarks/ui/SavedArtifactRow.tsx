import { ExternalLink } from "lucide-react";

import { useUnsave } from "@/features/bookmarks/api/use-save";
import type { SavedArtifactView } from "@/features/bookmarks/domain/saved";
import { HelpfulPrompt } from "@/features/bookmarks/ui/HelpfulPrompt";
import {
  CommunityBadge,
  SavedRowShell,
} from "@/features/bookmarks/ui/SavedRowShell";
import type { ArtifactType } from "@/features/knowledge/domain/artifact-type";
import { ArtifactLink } from "@/features/knowledge/ui/ArtifactLink";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";
import { relativeTime } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";

interface Props {
  item: SavedArtifactView;
  type: ArtifactType | undefined;
  /** False inside a community's own page, where every row shares that community. */
  showCommunity: boolean;
}

export function SavedArtifactRow({
  item,
  type,
  showCommunity,
}: Readonly<Props>) {
  const { artifact } = item;
  const unsave = useUnsave({ kind: "artifact", id: artifact.id });
  const now = useNow();
  return (
    <SavedRowShell
      icon={
        <TypeIcon
          typeId={artifact.typeId}
          label={artifact.type}
          icon={type?.icon}
          accentColor={type?.accentColor}
          size={20}
          className="size-10"
        />
      }
      onUnsave={() => unsave.mutate()}
      unsavePending={unsave.isPending}
    >
      <ArtifactLink
        artifactId={artifact.id}
        url={artifact.url}
        className="text-foreground hover:text-primary inline-flex items-center gap-1 font-semibold underline-offset-2 hover:underline"
      >
        {artifact.title}
        <ExternalLink size={13} className="text-primary shrink-0" />
      </ArtifactLink>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        {showCommunity && item.community && (
          <CommunityBadge community={item.community} />
        )}
        <p className="text-muted-foreground text-xs">
          {artifact.type} · saved {relativeTime(item.savedAt, now)}
        </p>
      </div>
      <div className="border-border/60 mt-3 border-t border-dashed pt-3">
        <HelpfulPrompt artifactId={artifact.id} helped={item.helped} />
      </div>
    </SavedRowShell>
  );
}
