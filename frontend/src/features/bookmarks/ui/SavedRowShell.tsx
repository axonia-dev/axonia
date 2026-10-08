import { Bookmark } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

import { KindBadge } from "@/components/patterns/KindBadge";
import { Button } from "@/components/ui/Button";
import type { SavedItem } from "@/features/bookmarks/domain/saved";

/**
 * The community pill linking to `/communities/:slug`, shared by every saved-item
 * row (artifact and session views carry the same community shape).
 */
export function CommunityBadge({
  community,
}: Readonly<{ community: NonNullable<SavedItem["community"]> }>) {
  return (
    <Link to={`/communities/${community.slug}`} className="shrink-0">
      <KindBadge
        kindId={community.id}
        label={community.name}
        accentColor={community.accentColor}
        className="hover:underline"
      />
    </Link>
  );
}

/** The trailing violet bookmark button every saved row uses to remove itself. */
function RemoveBookmarkButton({
  onPress,
  isDisabled,
}: Readonly<{ onPress: () => void; isDisabled: boolean }>) {
  return (
    <Button
      variant="ghost"
      size="sm"
      aria-label="Remove from saved"
      isDisabled={isDisabled}
      onPress={onPress}
      className="text-secondary absolute top-3 right-3 size-8 rounded-md p-0"
    >
      <Bookmark className="size-4 fill-current" />
    </Button>
  );
}

interface SavedRowShellProps {
  icon: ReactNode;
  children: ReactNode;
  onUnsave: () => void;
  unsavePending: boolean;
}

/**
 * The `<li>` shell shared by every saved-item row: a leading icon, a content
 * column, and the trailing remove-bookmark button. Each kind of row (artifact,
 * session) supplies its own icon and content — only the scaffolding lives here,
 * so `SavedArtifactRow` and `SavedSessionRow` diverge purely in their real content.
 */
export function SavedRowShell({
  icon,
  children,
  onUnsave,
  unsavePending,
}: Readonly<SavedRowShellProps>) {
  return (
    <li className="border-border bg-surface relative flex items-start gap-3 rounded-xl border p-4">
      {icon}
      <div className="min-w-0 flex-1 pr-9">{children}</div>
      <RemoveBookmarkButton onPress={onUnsave} isDisabled={unsavePending} />
    </li>
  );
}
