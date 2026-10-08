import { Calendar, ExternalLink } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { TagChips } from "@/components/ui/TagChips";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { ArtifactLink } from "@/features/knowledge/ui/ArtifactLink";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";
import { relativeTime } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";
import { userHue } from "@/lib/user-hue";

/** The contributor as the card should show them, resolved by the list. Everything `MemberAvatar`
 *  needs to draw a person but the subject, which the artifact names itself, because a partial
 *  answer here is what put a monogram on this one card while every other surface drew the face. */
export interface CardContributor {
  name: string;
  hue: string;
  avatarUrl: string | null;
}

interface Props {
  slug: string;
  artifact: Artifact;
  /** The fixed icon rail down the card's right edge: love, save. */
  actions?: ReactNode;
  /** The reaction strip, which belongs in the byline rather than the rail - see the footer. */
  reactions?: ReactNode;
  manageActions?: ReactNode;
  onTagClick?: (tag: string) => void;
  /** Resolved contributor identity; falls back to the raw user id. */
  contributor?: CardContributor;
  /** Resolved "series · topic (date)" provenance text; falls back to the snapshot label. */
  provenanceText?: string;
  /** The artifact type's stored Lucide icon name; falls back to keyword inference. */
  typeIcon?: string | null;
  /** The artifact type's stored accent color; falls back to the hashed hue. */
  typeAccent?: string | null;
}

/** "Captured from this session" - the artifact's provenance, as a quiet link.
 *
 *  `max-w-full` is what makes the truncation below work: `w-fit` hugs the text but does not
 *  cap it, so without the max the line sets the card's width and the card sets the page's -
 *  a series, a topic and a date do not fit the ~200px a phone leaves here, and the whole
 *  content area ends up scrolling sideways instead of the text ellipsing. A fixed cap in its
 *  place (this was `max-w-[22rem]`) only moves the problem: a nowrap line cannot shrink below
 *  it, so the cap itself becomes the width the page has to find. */
function SourceLine({
  slug,
  artifact,
  text,
}: Readonly<{ slug: string; artifact: Artifact; text: string | undefined }>) {
  if (!artifact.provenance) {
    return null;
  }
  const label = text ?? artifact.provenance.label ?? "a session";
  return (
    <Link
      to={`/communities/${slug}/sessions/${artifact.provenance.sessionId}`}
      className="text-muted-foreground hover:text-primary inline-flex w-fit max-w-full items-center gap-1.5 text-xs underline-offset-2 hover:underline"
    >
      <Calendar size={13} className="text-primary shrink-0" />
      <span className="truncate">{label}</span>
    </Link>
  );
}

function OpenLinkGlyph() {
  return (
    <span aria-hidden className="text-primary ml-1 inline-block align-[-1px]">
      <ExternalLink size={13} />
    </span>
  );
}

/** The three things `MemberAvatar` needs from a contributor, resolved together so the card cannot
 *  answer two of them. Extracted rather than inlined because `complexity: 10` counts every `??`. */
function contributorOf(
  contributor: CardContributor | undefined,
  userId: string,
): CardContributor {
  if (contributor) {
    return contributor;
  }
  return { name: userId, hue: userHue(userId), avatarUrl: null };
}

export function ArtifactCard({
  slug,
  artifact,
  actions,
  reactions,
  manageActions,
  onTagClick,
  contributor,
  provenanceText,
  typeIcon,
  typeAccent,
}: Readonly<Props>) {
  const person = contributorOf(contributor, artifact.contributorUserId);
  const now = useNow();

  return (
    <article className="group border-border bg-surface flex items-start gap-3 rounded-md border p-4">
      <TypeIcon
        typeId={artifact.typeId}
        label={artifact.type}
        icon={typeIcon}
        accentColor={typeAccent}
        size={20}
        className="size-10"
      />

      <div className="flex min-w-0 flex-1 flex-col gap-2">
        {/* Wraps once the title cannot keep 10rem beside the actions, so on a phone they drop
         *  under the title instead of squeezing it and spilling into the rail. */}
        <div className="flex flex-wrap items-start gap-2">
          <div className="min-w-0 grow basis-40">
            <ArtifactLink
              artifactId={artifact.id}
              url={artifact.url}
              className="text-foreground hover:text-primary font-medium underline-offset-2 hover:underline"
            >
              {artifact.title}
              <OpenLinkGlyph />
            </ArtifactLink>
          </div>
          {manageActions}
        </div>

        <SourceLine slug={slug} artifact={artifact} text={provenanceText} />

        {artifact.description !== "" && (
          <p className="text-muted-foreground line-clamp-2 text-sm">
            {artifact.description}
          </p>
        )}

        <TagChips tags={artifact.tags} onTagClick={onTagClick} />

        {/* Reactions ride the byline rather than the action rail beside it: the rail is a fixed
         *  40px column of two icon controls, and a strip that grows a chip per reaction pushes
         *  it wide enough to squash the title. Here they have the card's width to grow into. */}
        <footer className="text-muted-foreground flex flex-wrap items-center gap-1.5 text-xs">
          <span className="flex min-w-0 items-start gap-1.5">
            <MemberAvatar
              name={person.name}
              hue={person.hue}
              subject={artifact.contributorUserId}
              src={person.avatarUrl}
              className="size-5 rounded-full text-[0.6rem]"
            />
            <span className="leading-5">
              {person.name} ·{" "}
              <span className="whitespace-nowrap">
                {relativeTime(artifact.createdAt, now)}
              </span>
            </span>
          </span>
          {reactions && <div className="ml-auto">{reactions}</div>}
        </footer>
      </div>

      {actions && (
        <div className="border-border flex shrink-0 flex-col items-center justify-center gap-1.5 self-stretch border-l pl-3">
          {actions}
        </div>
      )}
    </article>
  );
}
