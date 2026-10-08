// frontend/src/features/community/ui/MembersPage.tsx
import { useState } from "react";
import { useParams } from "react-router-dom";

import { currentUserId } from "@/auth/current-user";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useChangeRole,
  useMembers,
} from "@/features/community/api/use-membership";
import { communityErrorMessage } from "@/features/community/community-errors";
import {
  countByRole,
  type DirectorySegment,
  filterMembers,
} from "@/features/community/domain/member-directory";
import type {
  CommunityRole,
  Membership,
} from "@/features/community/domain/membership";
import { useCommunityCrumbs } from "@/features/community/ui/community-chrome";
import { MemberRow } from "@/features/community/ui/MemberRow";
import { MembersToolbar } from "@/features/community/ui/MembersToolbar";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";

const MEMBER_LOOKUP = { page: 0, size: 100 } as const;

interface MemberListProps {
  roster: readonly Membership[];
  visible: readonly Membership[];
  directory: Record<string, User>;
  me: string | undefined;
  pendingUserId: string | null;
  rowErrors: Record<string, string>;
  onChangeRole: (userId: string, role: CommunityRole) => void;
}

function MemberList({
  roster,
  visible,
  directory,
  me,
  pendingUserId,
  rowErrors,
  onChangeRole,
}: Readonly<MemberListProps>) {
  if (visible.length === 0) {
    return (
      <p className="text-muted-foreground text-sm">
        {roster.length === 0
          ? "No members yet."
          : "No members match this search."}
      </p>
    );
  }
  return (
    <ul className="flex flex-col gap-2">
      {visible.map((member) => (
        <MemberRow
          key={member.id}
          member={member}
          user={directory[member.userId]}
          isYou={member.userId === me}
          isDisabled={pendingUserId === member.userId}
          errorMessage={rowErrors[member.userId]}
          onChangeRole={(role) => onChangeRole(member.userId, role)}
        />
      ))}
    </ul>
  );
}

export function MembersPage() {
  const { slug = "" } = useParams();
  const access = useManagementAccess(slug);
  const members = useMembers(slug, MEMBER_LOOKUP);
  const changeRole = useChangeRole(slug);
  const [segment, setSegment] = useState<DirectorySegment>("all");
  const [query, setQuery] = useState("");
  const [pendingUserId, setPendingUserId] = useState<string | null>(null);
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({});
  useCommunityCrumbs([{ label: "Members" }]);

  const roster = members.data?.content ?? [];
  const users = useUsers(roster.map((member) => member.userId));

  function onChangeRole(userId: string, role: CommunityRole) {
    setPendingUserId(userId);
    changeRole.mutate(
      { userId, role },
      {
        onError: (error) =>
          setRowErrors((prev) => ({
            ...prev,
            [userId]: communityErrorMessage(error),
          })),
        onSuccess: () =>
          setRowErrors((prev) => {
            const next = { ...prev };
            delete next[userId];
            return next;
          }),
        onSettled: () =>
          setPendingUserId((current) => (current === userId ? null : current)),
      },
    );
  }

  if (!access.members) {
    return (
      <p className="text-muted-foreground text-sm">
        No access to manage members here.
      </p>
    );
  }
  if (members.isPending) {
    return <p className="text-muted-foreground text-sm">Loading&hellip;</p>;
  }
  if (members.isError || !members.data) {
    return (
      <p role="alert" className="text-muted-foreground text-sm">
        Could not load the members.
      </p>
    );
  }

  const directory = users.data ?? {};
  const counts = countByRole(roster);
  const visible = filterMembers(roster, directory, query, segment);
  const me = currentUserId();

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-col gap-1">
        <h1 className="text-foreground font-display text-xl font-semibold">
          Members &amp; roles
        </h1>
        <p className="text-muted-foreground text-sm">
          Everyone in this community. Organizers can manage its settings,
          members, and content: switch a member to organizer to share that.
        </p>
      </header>
      <MembersToolbar
        counts={counts}
        segment={segment}
        onSegmentChange={setSegment}
        query={query}
        onQueryChange={setQuery}
      />
      <MemberList
        roster={roster}
        visible={visible}
        directory={directory}
        me={me}
        pendingUserId={pendingUserId}
        rowErrors={rowErrors}
        onChangeRole={onChangeRole}
      />
    </section>
  );
}
