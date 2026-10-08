import type { LucideIcon } from "lucide-react";
import type { ComponentType, PropsWithChildren } from "react";
import type { RouteObject } from "react-router-dom";

import type {
  SettingsGroup,
  SettingsNavItem,
} from "@/components/patterns/settings-nav";
import type { PermissionGroupContribution } from "@/features/admin/domain/edition-permissions";
import type { Community } from "@/features/community/domain/community";
import type { RetryableRead } from "@/lib/failed-to-load";

export type CommunityDialogProps = Readonly<{
  slug: string;
  community: Community;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}>;

/** A card an edition puts first on the organization Notifications page. It gates itself; holders
 *  of any of `reveals` are offered the page as holders of `communication:write` are, and
 *  `permissionGroup` is what the roles matrix offers for it. */
export type NotificationsCard = Readonly<{
  Card: ComponentType;
  reveals: ReadonlyArray<string>;
  permissionGroup: PermissionGroupContribution;
}>;

/** Where a session's organization comes from: the token's `org` claim, which a session may lack
 *  until it enters one, or the installation itself, which has exactly one and no IdP to name it. */
type OrganizationScope = "token" | "installation";

export type Edition = {
  publicRoutes: RouteObject[];
  authenticatedRoutes: RouteObject[];
  settingsSections: SettingsSection[];
  accountPages: AccountPage[];
  accountMenuItems: AccountMenuItem[];
  AvatarDecoration: ComponentType<{ subject: string }> | null;
  RootRedirect: ComponentType;
  ShellGate: ComponentType<PropsWithChildren>;
  HelpEntry: ComponentType<HelpEntryProps> | null;
  OrganizationPicker: ComponentType<OrganizationPickerProps> | null;
  CommunityInvite: ComponentType<CommunityDialogProps> | null;
  CommunityDeletion: ComponentType<CommunityDialogProps> | null;
  AppFrame: ComponentType<PropsWithChildren>;
  /** The account menu's way out of a delegated session, in Log out's place; null where the
   *  edition opens no delegated session, and a delegated tab then offers no sign-out at all. */
  DelegatedSessionExit: ComponentType | null;
  sessionlessPaths: ReadonlyArray<string>;
  AgentAccessNotice: ComponentType | null;
  PrivacyNotice: ComponentType | null;
  notificationsCard: NotificationsCard | null;
  organizationScope: OrganizationScope;
};

export type EditionSlots = Omit<
  Edition,
  "publicRoutes" | "authenticatedRoutes" | "AppFrame" | "sessionlessPaths"
>;

export type SettingsSection = Readonly<{
  group: SettingsGroup;
  pages: Readonly<Record<string, ComponentType>>;
  /** Set when the items join an open group rather than standing as a group of their own. */
  joins?: "people-and-access";
  /** Old paths that redirect to one of `pages`, relative like `MOVED`. */
  moved?: ReadonlyArray<readonly [string, string]>;
  /** The permissions the section's pages are gated on, offered by the roles matrix as a group of
   *  their own. An edition without the section neither offers nor knows them. */
  permissionGroup?: PermissionGroupContribution;
}>;

export type AccountPage = Readonly<{
  item: SettingsNavItem;
  groupLabel: string;
  /** Set when the page joins the account's own group rather than standing in `groupLabel`'s. */
  joins?: "account";
  Page: ComponentType;
  /** `failure` is set when the read behind the offer failed, so the route says so rather than
   *  reading the failure as "not offered". */
  useOffer: () => Readonly<{
    offered: boolean;
    isLoading: boolean;
    failure?: RetryableRead | undefined;
  }>;
  moved: ReadonlyArray<readonly [string, string]>;
}>;

type AccountMenuItem = Readonly<{
  id: string;
  label: string;
  href: string;
  icon: LucideIcon;
}>;

export type OrganizationPickerProps = Readonly<{
  returnTo: string;
  layout: "select" | "list" | "groups";
}>;

type HelpEntryProps = Readonly<{
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}>;
