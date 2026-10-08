import { X } from "lucide-react";
import { type ReactNode, useState } from "react";

import { useIsDelegated } from "@/auth/delegated-session";
import { IconButton } from "@/components/ui/IconButton";
import { Select } from "@/components/ui/Select";
import { useRemoveDeclaration } from "@/features/expertise/api/use-declarations";
import { useUnhideTag } from "@/features/expertise/api/use-expertise-prefs";
import { ALL_COMMUNITIES } from "@/features/expertise/domain/area-filter";
import { canDeclareFrom } from "@/features/expertise/domain/declared-areas";
import type {
  DeclarableCommunity,
  SelfDeclaration,
} from "@/features/expertise/domain/expertise";
import { AddDeclarationField } from "@/features/expertise/ui/AddDeclarationField";
import { AreaVisibilityButton } from "@/features/expertise/ui/AreaVisibilityButton";
import { TIER_META } from "@/features/expertise/ui/tier-meta";
import { cn } from "@/lib/cn";
import { declaredOn } from "@/lib/declared-on";

interface Props {
  /** The declared-only rows to list, already scoped to the page and filtered by the toolbar. */
  declarations: SelfDeclaration[];
  /** Set on a community's page, which lists that community's declarations. */
  slug?: string | undefined;
  /** The toolbar's community lens, which only the page across communities offers. */
  communityId: number;
  /** Where the member can declare: on a community's page, that community or none. */
  declarableCommunities: DeclarableCommunity[];
}

interface RowProps {
  declaration: SelfDeclaration;
  showCommunity: boolean;
  isRemoving: boolean;
  onUnhide: (tag: string) => void;
  /** Absent where the session may not change declarations. */
  onRemove?: ((declaration: SelfDeclaration) => void) | undefined;
}

function DeclaredRow({
  declaration,
  showCommunity,
  isRemoving,
  onUnhide,
  onRemove,
}: Readonly<RowProps>) {
  const date = `Declared ${declaredOn(declaration.declaredAt)}`;
  return (
    <div
      className={cn(
        "border-border bg-surface flex items-center gap-3 rounded-xl border border-dashed px-4 py-3",
        declaration.hidden && "opacity-60",
      )}
    >
      <span
        aria-hidden
        className={cn(
          "h-8 w-[3px] shrink-0 rounded-full",
          TIER_META.OPEN_TO_QUESTIONS.dotClassName,
        )}
      />
      <div className="min-w-0 flex-1">
        <p className="text-foreground truncate font-medium">
          {declaration.tag}
          {declaration.hidden && (
            <span className="text-muted-foreground ml-2 text-xs font-normal">
              · hidden from others
            </span>
          )}
        </p>
        <p className="text-muted-foreground mt-0.5 text-xs">
          {showCommunity ? `${declaration.communityName} · ${date}` : date}
        </p>
      </div>
      {/* A curator's merge or split can hide a declared area, and no evidence row offers it back. */}
      {declaration.hidden && (
        <AreaVisibilityButton
          tag={declaration.tag}
          community={showCommunity ? declaration.communityName : undefined}
          hidden
          onPress={() => onUnhide(declaration.tag)}
        />
      )}
      {onRemove && (
        <IconButton
          label={
            showCommunity
              ? `Remove ${declaration.tag} from ${declaration.communityName}`
              : `Remove ${declaration.tag}`
          }
          tone="danger"
          isPending={isRemoving}
          onPress={() => onRemove(declaration)}
        >
          <X aria-hidden />
        </IconButton>
      )}
    </div>
  );
}

function LaneHeading({ count }: Readonly<{ count?: number }>) {
  return (
    <div className="flex items-center gap-2">
      <span
        aria-hidden
        className={cn(
          "size-2 rounded-full",
          TIER_META.OPEN_TO_QUESTIONS.dotClassName,
        )}
      />
      <span className="text-foreground text-sm font-semibold">
        {TIER_META.OPEN_TO_QUESTIONS.label}
      </span>
      {count !== undefined && (
        <span className="text-muted-foreground text-xs">· {count}</span>
      )}
      <span className="bg-border ml-1 h-px flex-1" />
    </div>
  );
}

interface LaneProps {
  declarations: SelfDeclaration[];
  showCommunity: boolean;
  addField: ReactNode;
}

/** Not collapsible like the tier lanes: it holds the add field, and a folded field goes unfound.
 *  A delegated session is refused every declaration write, so it is offered none. */
function Lane({ declarations, showCommunity, addField }: Readonly<LaneProps>) {
  const remove = useRemoveDeclaration();
  const unhide = useUnhideTag();
  const writable = !useIsDelegated();
  const field = writable ? addField : null;

  if (declarations.length === 0) {
    return field === null ? null : (
      <section className="flex flex-col gap-2">
        <LaneHeading />
        {field}
      </section>
    );
  }

  return (
    <section className="flex flex-col gap-2">
      <LaneHeading count={declarations.length} />
      <p className="text-muted-foreground text-xs">
        Declared, not yet shown in activity.
      </p>
      {declarations.map((declaration) => (
        <DeclaredRow
          key={`${declaration.communityId}|${declaration.tag}`}
          declaration={declaration}
          showCommunity={showCommunity}
          isRemoving={remove.isRemoving({
            slug: declaration.communitySlug,
            tag: declaration.tag,
          })}
          onUnhide={(tag) => unhide.mutate(tag)}
          onRemove={
            writable
              ? (d) => remove.mutate({ slug: d.communitySlug, tag: d.tag })
              : undefined
          }
        />
      ))}
      {field}
    </section>
  );
}

/** A non member and an archived community take no declaration, and the server's view already says so. */
function CommunityLane({
  declarations,
  slug,
  declarableCommunities,
}: Readonly<Omit<Props, "communityId"> & { slug: string }>) {
  return (
    <Lane
      declarations={declarations}
      showCommunity={false}
      addField={
        canDeclareFrom(declarableCommunities, slug) ? (
          <AddDeclarationField slug={slug} />
        ) : null
      }
    />
  );
}

function declarableUnderLens(
  communities: DeclarableCommunity[],
  communityId: number,
) {
  return communityId === ALL_COMMUNITIES
    ? communities
    : communities.filter((community) => community.id === communityId);
}

/** Across communities the member picks where to declare, narrowed to the lens: a declaration made under another
 *  community's lens would vanish on arrival. */
function AllCommunitiesLane({
  declarations,
  communityId,
  declarableCommunities,
}: Readonly<Omit<Props, "slug">>) {
  const [chosen, setChosen] = useState<string | null>(null);
  const options = declarableUnderLens(declarableCommunities, communityId).map(
    (community) => ({ id: community.slug, label: community.name }),
  );
  const slug =
    options.find((option) => option.id === chosen)?.id ?? options[0]?.id;
  return (
    <Lane
      declarations={declarations}
      showCommunity
      addField={
        slug === undefined ? null : (
          <AddDeclarationField
            slug={slug}
            picker={
              <Select
                label="Community to declare in"
                hideLabel
                value={slug}
                options={options}
                onChange={setChosen}
              />
            }
          />
        )
      }
    />
  );
}

export function DeclaredLane({
  declarations,
  slug,
  communityId,
  declarableCommunities,
}: Readonly<Props>) {
  return slug === undefined ? (
    <AllCommunitiesLane
      declarations={declarations}
      communityId={communityId}
      declarableCommunities={declarableCommunities}
    />
  ) : (
    <CommunityLane
      declarations={declarations}
      slug={slug}
      declarableCommunities={declarableCommunities}
    />
  );
}
