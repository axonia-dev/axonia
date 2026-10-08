import { useState } from "react";

import { EmptyState } from "@/components/patterns/EmptyState";
import { ListRow } from "@/components/patterns/ListRow";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { PageHeader } from "@/components/patterns/PageHeader";
import { Pagination } from "@/components/patterns/Pagination";
import { SearchField } from "@/components/ui/SearchField";
import { Skeleton } from "@/components/ui/Skeleton";
import { useAdminUsers } from "@/features/admin/api/use-admin-users";
import type { AdminUser } from "@/features/admin/domain/admin-user";
import { formatLastActive } from "@/features/admin/domain/format-last-active";
import { RequirePermission } from "@/features/admin/ui/RequirePermission";
import { RoleChip } from "@/features/admin/ui/RoleChip";
import { UserDetailDrawer } from "@/features/admin/ui/UserDetailDrawer";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { MemberLabel } from "@/features/identity/ui/MemberLabel";
import { failedToLoad } from "@/lib/failed-to-load";
import { userHue } from "@/lib/user-hue";

// A SCIM-provisioned member who has never signed in has no attached identity
// (userId is null); the email is the stable fallback for keys, avatar colour and
// avatar lookups. Both can be absent - SCIM accepts a user with no userName - and
// the display name is then the only handle left.
function identityKey(user: AdminUser): string {
  return user.userId ?? user.email ?? user.displayName;
}

function PendingBadge() {
  return (
    <span className="border-warning/40 bg-warning/10 text-warning inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium">
      Pending
    </span>
  );
}

function UserAvatar({
  user,
  avatarUrl,
}: Readonly<{
  user: AdminUser;
  avatarUrl: string | null;
}>) {
  const hue = userHue(identityKey(user));
  const { active } = formatLastActive(user.lastActiveAt);
  return (
    <span className="relative inline-flex shrink-0">
      <MemberAvatar
        name={user.displayName}
        hue={hue}
        subject={user.userId}
        src={avatarUrl}
      />
      <span
        aria-hidden="true"
        // Top-right, on the circle's rim, because bottom-right is left to an edition's avatar decoration.
        className={`border-surface absolute top-0 right-0 size-2.5 rounded-full border-2 ${
          active ? "bg-success" : "bg-border-strong"
        }`}
      />
    </span>
  );
}

function UserRow({
  user,
  avatarUrl,
  onSelect,
}: Readonly<{
  user: AdminUser;
  avatarUrl: string | null;
  onSelect: (u: AdminUser) => void;
}>) {
  return (
    <li>
      <ListRow
        title={
          <MemberLabel name={user.displayName} status={user.memberStatus} />
        }
        meta={formatLastActive(user.lastActiveAt).label}
        accent={userHue(identityKey(user))}
        leading={<UserAvatar user={user} avatarUrl={avatarUrl} />}
        onPress={() => onSelect(user)}
        trailing={
          <span className="flex items-center gap-1.5">
            {user.userId === null && <PendingBadge />}
            {user.effectiveRoles.map((er) => (
              <RoleChip
                key={`${er.role}-${er.via}`}
                roleName={er.role}
                roleId={er.roleId}
                via={er.via}
              />
            ))}
            <span aria-hidden="true" className="text-border-strong text-lg">
              ›
            </span>
          </span>
        }
      />
    </li>
  );
}

function UserList({
  users,
  avatars,
  q,
  onSelect,
}: Readonly<{
  users: AdminUser[];
  avatars: Record<string, User> | undefined;
  q: string;
  onSelect: (u: AdminUser) => void;
}>) {
  if (users.length === 0) {
    return (
      <EmptyState
        title="No users found"
        description={q ? "Try a different search." : "No users yet."}
      />
    );
  }
  return (
    <ul className="flex flex-col gap-2">
      {users.map((user) => (
        <UserRow
          key={identityKey(user)}
          user={user}
          avatarUrl={
            user.userId ? (avatars?.[user.userId]?.avatarUrl ?? null) : null
          }
          onSelect={onSelect}
        />
      ))}
    </ul>
  );
}

/** The list area: an unread list says so rather than reading as "No users yet." */
function UsersBody({
  users,
  avatars,
  q,
  onSelect,
}: Readonly<{
  users: ReturnType<typeof useAdminUsers>;
  avatars: Record<string, User> | undefined;
  q: string;
  onSelect: (u: AdminUser) => void;
}>) {
  if (failedToLoad(users)) {
    return (
      <LoadFailed inline message="Could not load the users." query={users} />
    );
  }
  if (users.isPending) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    );
  }
  return (
    <UserList
      users={users.data?.content ?? []}
      avatars={avatars}
      q={q}
      onSelect={onSelect}
    />
  );
}

function UserCount({ count }: Readonly<{ count: number }>) {
  return (
    <span className="border-border bg-surface text-muted-foreground rounded-full border px-2 py-0.5 text-xs font-semibold">
      {count} {count === 1 ? "person" : "people"}
    </span>
  );
}

function UsersPageContent() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  // Only the selection is state; the row itself is read back from the query on every render.
  // Granting or revoking invalidates that query, so a snapshot taken at open time would leave
  // the drawer showing pre-mutation roles for as long as it stays open.
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const query = useAdminUsers({
    ...(q ? { q } : {}),
    page,
  });
  const users = query.data;
  const rows = users?.content ?? [];
  const avatars = useUsers(
    rows.map((user) => user.userId).filter((id): id is string => id !== null),
  );
  const selectedUser =
    rows.find((user) => identityKey(user) === selectedKey) ?? null;

  // Narrowing the search re-pages from the start: page 3 of "everyone" is almost never a
  // page of "alice", and an out-of-range page answers with an empty list, which reads as
  // "no such user" rather than "you are past the end".
  const search = (value: string) => {
    setQ(value);
    setPage(0);
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-3">
        <PageHeader title="Users" />
        {users && <UserCount count={users.totalElements} />}
      </div>
      <SearchField
        label="Search users"
        value={q}
        onChange={search}
        placeholder="Search by name or email…"
        className="max-w-sm"
      />

      <UsersBody
        users={query}
        avatars={avatars.data}
        q={q}
        onSelect={(user) => setSelectedKey(identityKey(user))}
      />

      {users && users.totalPages > 1 && (
        <Pagination
          page={users.number}
          totalPages={users.totalPages}
          onPageChange={setPage}
        />
      )}

      <UserDetailDrawer
        user={selectedUser}
        isOpen={selectedUser !== null}
        onClose={() => setSelectedKey(null)}
      />
    </div>
  );
}

export function UsersPage() {
  return (
    <RequirePermission permission="member:read" title="Users">
      <UsersPageContent />
    </RequirePermission>
  );
}
