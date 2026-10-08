import { useParams } from "react-router-dom";

import { currentUserId } from "@/auth/current-user";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import type { Topic } from "@/features/events/domain/topic";
import { BacklogView } from "@/features/events/ui/BacklogView";
import { ReturnToBacklogButton } from "@/features/events/ui/ReturnToBacklogButton";
import { SpeakerSuggestions } from "@/features/expertise/ui/SpeakerSuggestions";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { ArtifactActions } from "@/features/knowledge/ui/ArtifactActions";
import { LibraryView } from "@/features/knowledge/ui/LibraryView";
import { LoveAction } from "@/features/recognition/ui/LoveAction";
import { ReactionBar } from "@/features/recognition/ui/ReactionBar";

export function BacklogSection() {
  const { slug = "" } = useParams();
  const canManage = useIsOrganizer(slug);

  function renderTopicActions(topic: Topic) {
    if (topic.kind !== "REQUEST") return null;
    return <SpeakerSuggestions slug={slug} tags={topic.tags} />;
  }

  return (
    <BacklogView
      slug={slug}
      renderTopicActions={renderTopicActions}
      {...(canManage
        ? {
            renderScheduledActions: (topic: Topic) => (
              <ReturnToBacklogButton slug={slug} topicId={topic.id} />
            ),
          }
        : {})}
    />
  );
}

export function LibrarySection() {
  const { slug = "" } = useParams();
  const canManage = useIsOrganizer(slug);
  const canCredit = useManagementAccess(slug).credit;

  function contributionOf(artifact: Artifact) {
    return {
      contributionType: "ARTIFACT_CAPTURED" as const,
      contributionId: artifact.id,
    };
  }

  /** The rail takes the two fixed icon controls only. The reaction strip grows a chip per
   *  reaction, which a 40px column cannot hold, so it rides the byline instead. */
  function renderArtifactActions(artifact: Artifact) {
    return (
      <LoveAction
        layout="stacked"
        contribution={contributionOf(artifact)}
        contributorUserIds={[artifact.contributorUserId]}
      />
    );
  }

  function renderArtifactReactions(artifact: Artifact) {
    return <ReactionBar contribution={contributionOf(artifact)} />;
  }

  function renderArtifactManageActions(artifact: Artifact) {
    const canEdit = canManage || artifact.contributorUserId === currentUserId();
    if (!canEdit && !canCredit) return null;
    return (
      <ArtifactActions
        artifact={artifact}
        slug={slug}
        canEdit={canEdit}
        canCredit={canCredit}
      />
    );
  }

  return (
    <LibraryView
      slug={slug}
      renderArtifactActions={renderArtifactActions}
      renderArtifactReactions={renderArtifactReactions}
      renderArtifactManageActions={renderArtifactManageActions}
    />
  );
}
