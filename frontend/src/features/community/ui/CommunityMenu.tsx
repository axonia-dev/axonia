import { Fragment, useState } from "react";
import type { Key } from "react-aria-components";
import { useNavigate } from "react-router-dom";

import { MenuButton, MenuItem, MenuSeparator } from "@/components/ui/Menu";
import { useEdition } from "@/edition-context";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useLeaveCommunity } from "@/features/community/api/use-leave-community";
import type { ManagementAccess } from "@/features/community/api/use-management-access";
import { canEdit, type Community } from "@/features/community/domain/community";
import { ArchiveCommunityDialog } from "@/features/community/ui/ArchiveCommunityDialog";
import { LeaveCommunityDialog } from "@/features/community/ui/LeaveCommunityDialog";

interface Props {
  community: Community;
  slug: string;
  access: ManagementAccess;
}

// Kept outside CommunityMenu so the nullish-heavy organization lookup (and the
// access-flag check it folds in) doesn't add to the component's own eslint
// `complexity` count (each `?.`/`&&` counts as a branch). A self-serve org's
// single community can never be archived (it would soft-lock the tenant), so
// the backend refuses it and the entry never appears for a confirmed hub
// org; pending/unknown org data shows it as today.
function useShowArchive(accessArchive: boolean): boolean {
  const { data: organization } = useCurrentOrganization();
  return accessArchive && organization?.kind !== "HUB";
}

// Deletion is the hub's counterpart of Archive: org-level, Owner-held
// (organization:delete), only meaningful where Archive is suppressed, and
// offered only where the edition supplies the dialog. Kept outside the
// component for the same eslint-complexity reason as useShowArchive.
function useShowDelete(deletable: boolean): boolean {
  const { data: organization } = useCurrentOrganization();
  const { can } = usePermissions();
  return (
    deletable && organization?.kind === "HUB" && can("organization:delete")
  );
}

interface MenuItemSpec {
  id: string;
  label: string;
  danger?: boolean;
}

// Split in two so neither half's branch count creeps toward the shared
// eslint `complexity` ceiling: the non-destructive items (edit surfaces) and
// the destructive ones (archive/delete) each stay comfortably under it on
// their own, and CommunityMenu itself never sees any of these `&&`/`||`.
function coreMenuItems(
  editable: boolean,
  access: ManagementAccess,
): MenuItemSpec[] {
  const items: MenuItemSpec[] = [];
  if (editable && access.editCommunity) {
    items.push({ id: "edit", label: "Edit" });
  }
  if (editable && access.lookAndFeel) {
    items.push({ id: "look-and-feel", label: "Look & feel" });
  }
  if (access.members) {
    items.push({ id: "members", label: "Members & roles" });
  }
  if (access.contributionPolicy || access.managementPolicy) {
    items.push({ id: "community-policy", label: "Community policy" });
  }
  if (access.communication) {
    items.push({
      id: "communication-settings",
      label: "Communication",
    });
  }
  return items;
}

function destructiveMenuItems(
  editable: boolean,
  showArchive: boolean,
  showDelete: boolean,
): MenuItemSpec[] {
  const items: MenuItemSpec[] = [];
  if (editable && showArchive) {
    items.push({ id: "archive", label: "Archive", danger: true });
  }
  if (showDelete) {
    items.push({ id: "delete", label: "Delete", danger: true });
  }
  return items;
}

/** The menu in three groups: what you may manage, how the community ends, and
 *  the one item that is about you rather than about the community. Leave sits
 *  alone at the bottom so it is never a call to action and never adjacent to
 *  Archive or Delete. */
function menuGroups({
  editable,
  access,
  showArchive,
  showDelete,
}: Readonly<{
  editable: boolean;
  access: ManagementAccess;
  showArchive: boolean;
  showDelete: boolean;
}>): MenuItemSpec[][] {
  const groups = [
    coreMenuItems(editable, access),
    destructiveMenuItems(editable, showArchive, showDelete),
    [{ id: "leave", label: "Leave community", danger: true }],
  ];
  return groups.filter((group) => group.length > 0);
}

const SECTION_PATHS: Record<string, string> = {
  edit: "edit",
  members: "settings/members",
  "community-policy": "settings/policy",
  "look-and-feel": "look-and-feel",
  "communication-settings": "settings/communication",
};

function sectionPath(slug: string, key: Key): string {
  return `/communities/${slug}/${SECTION_PATHS[String(key)] ?? ""}`;
}

/**
 * The community's one control surface: a single kebab gathering every
 * management action, ending in the one item that is about the member rather
 * than the community. Each management item appears only for a user the
 * management policy lets reach that section; Leave community is always
 * there, because every member may leave. Edit, Look & feel and Archive fall
 * away once the community is archived; only what is still meaningful stays.
 */
export function CommunityMenu({ community, slug, access }: Readonly<Props>) {
  const navigate = useNavigate();
  const [archiving, setArchiving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const leave = useLeaveCommunity(slug);
  const editable = canEdit(community);
  const showArchive = useShowArchive(access.archive);
  const { CommunityDeletion } = useEdition();
  const showDelete = useShowDelete(CommunityDeletion !== null);
  const groups = menuGroups({ editable, access, showArchive, showDelete });

  function leaveOrConfirm() {
    if (leave.isHub) {
      setLeaving(true);
      return;
    }
    leave.leave();
  }

  function onAction(key: Key) {
    if (key === "leave") {
      leaveOrConfirm();
    } else if (key === "archive") {
      setArchiving(true);
    } else if (key === "delete") {
      setDeleting(true);
    } else {
      void navigate(sectionPath(slug, key));
    }
  }

  return (
    <>
      <MenuButton
        label="Community actions"
        onAction={onAction}
        trigger={
          <span
            aria-hidden="true"
            className="text-muted-foreground hover:text-foreground flex size-8 items-center justify-center rounded-md text-lg leading-none"
          >
            ⋯
          </span>
        }
      >
        {groups.map((group, index) => (
          <Fragment key={group[0]?.id ?? index}>
            {index > 0 && <MenuSeparator />}
            {group.map((item) => (
              <MenuItem
                key={item.id}
                id={item.id}
                isDisabled={item.id === "leave" && !leave.isReady}
                tone={item.danger === true ? "danger" : "default"}
              >
                {item.label}
              </MenuItem>
            ))}
          </Fragment>
        ))}
      </MenuButton>
      <ArchiveCommunityDialog
        slug={slug}
        isOpen={archiving}
        onOpenChange={setArchiving}
      />
      {/* Mounted whenever the edition supplies it, as before, not only while Delete is offered: a
          deletion refetches the organization it removes, and the dialog must outlive that refetch
          to send the caller on. */}
      {CommunityDeletion !== null && (
        <CommunityDeletion
          slug={slug}
          community={community}
          isOpen={deleting}
          onOpenChange={setDeleting}
        />
      )}
      <LeaveCommunityDialog
        slug={slug}
        name={community.name}
        isOpen={leaving}
        onOpenChange={setLeaving}
      />
    </>
  );
}
