import { Book, Unlink } from "lucide-react";
import { useRef, useState } from "react";
import { Focusable, TooltipTrigger } from "react-aria-components";
import { Link } from "react-router-dom";

import { currentUserId } from "@/auth/current-user";
import { Button } from "@/components/ui/Button";
import { Tooltip } from "@/components/ui/Tooltip";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import {
  canDetachResource,
  type SessionResource,
} from "@/features/knowledge/domain/session-resource";
import { ArtifactLink } from "@/features/knowledge/ui/ArtifactLink";
import { TypeIcon } from "@/features/knowledge/ui/type-visual";

interface Props {
  resource: SessionResource;
  canManage?: boolean;
  onDetach: () => void;
  slug?: string;
}

/**
 * The type's own glyph and colour, or none while the catalogue has not loaded.
 *
 * The artifact carries its type's id and label, never what the community chose to draw it with,
 * so the catalogue is joined here the same way `ArtifactList` joins it. Without this the row falls
 * back to a hash of the id and a glyph guessed from the label, and every resource on a session
 * page disagrees with the same artifact's own card in the library.
 */
function useTypeVisual(typeId: number) {
  const types = useArtifactTypes().data ?? [];
  const type = types.find((candidate) => candidate.id === typeId);
  if (!type) return {};
  return { icon: type.icon, accentColor: type.accentColor };
}

/** Whether the link's label has collapsed to its screen-reader-only box, leaving the icon alone. */
function isIconOnly(label: HTMLElement | null): boolean {
  return label !== null && label.getBoundingClientRect().width <= 1;
}

/**
 * On a narrow row the label collapses to an icon, and only then does the tooltip name it: beside
 * the visible words it would say them twice. The collapse is a container query, so the label's own
 * box is what tells the two apart.
 */
function LibraryLink({
  slug,
  title,
}: Readonly<{ slug: string; title: string }>) {
  const label = useRef<HTMLSpanElement>(null);
  const [isOpen, setOpen] = useState(false);
  return (
    <TooltipTrigger
      delay={300}
      isOpen={isOpen}
      onOpenChange={(open) => setOpen(open && isIconOnly(label.current))}
    >
      <Focusable>
        <Link
          to={`/communities/${slug}/library?q=${encodeURIComponent(title)}`}
          className="text-muted-foreground hover:text-primary flex items-center rounded-sm text-xs whitespace-nowrap underline-offset-2 hover:underline @max-sm:p-1.5"
        >
          <Book size={15} aria-hidden className="hidden @max-sm:block" />
          <span ref={label} className="@max-sm:sr-only">
            View in library
          </span>
        </Link>
      </Focusable>
      <Tooltip placement="top">View in library</Tooltip>
    </TooltipTrigger>
  );
}

function DetachButton({ onDetach }: Readonly<{ onDetach: () => void }>) {
  return (
    <TooltipTrigger delay={300}>
      <Button
        variant="ghost"
        size="sm"
        aria-label="Detach"
        className="text-muted-foreground hover:text-primary p-1.5 hover:bg-transparent"
        onPress={onDetach}
      >
        <Unlink size={15} aria-hidden />
      </Button>
      <Tooltip placement="top">Detach</Tooltip>
    </TooltipTrigger>
  );
}

export function ResourceRow({
  resource,
  canManage = false,
  onDetach,
  slug,
}: Readonly<Props>) {
  const { artifact } = resource;
  const canDetach = canDetachResource(resource, canManage, currentUserId());
  const showTrailing = slug !== undefined || canDetach;
  const typeVisual = useTypeVisual(artifact.typeId);

  return (
    <li className="border-border bg-surface resource-enter @container flex items-center gap-2 rounded-sm border px-2.5 py-1.5 @max-sm:gap-1.5 @max-sm:px-2">
      <TypeIcon
        typeId={artifact.typeId}
        label={artifact.type}
        {...typeVisual}
        size={14}
        className="p-1"
      />
      <span className="sr-only">{artifact.type}</span>
      <ArtifactLink
        artifactId={artifact.id}
        url={artifact.url}
        className="text-foreground hover:text-primary min-w-0 truncate text-sm underline-offset-2 hover:underline"
      >
        {artifact.title}
      </ArtifactLink>
      {showTrailing && (
        <div className="ml-auto flex shrink-0 items-center gap-2 @max-sm:gap-0">
          {slug !== undefined && (
            <LibraryLink slug={slug} title={artifact.title} />
          )}
          {canDetach && <DetachButton onDetach={onDetach} />}
        </div>
      )}
    </li>
  );
}
