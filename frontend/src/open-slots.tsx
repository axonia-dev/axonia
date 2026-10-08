import type { PropsWithChildren } from "react";
import { Navigate } from "react-router-dom";

import type { EditionSlots } from "@/edition";

function CommunitiesRedirect() {
  return <Navigate to="/communities" replace />;
}

export function PassThrough({ children }: Readonly<PropsWithChildren>) {
  return <>{children}</>;
}

export const openSlots: EditionSlots = {
  settingsSections: [],
  accountPages: [],
  accountMenuItems: [],
  AvatarDecoration: null,
  RootRedirect: CommunitiesRedirect,
  ShellGate: PassThrough,
  HelpEntry: null,
  OrganizationPicker: null,
  CommunityInvite: null,
  CommunityDeletion: null,
  AgentAccessNotice: null,
  PrivacyNotice: null,
  DelegatedSessionExit: null,
  notificationsCard: null,
  organizationScope: "token",
};
