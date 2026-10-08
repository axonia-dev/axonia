import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { formatJoinedMonth } from "@/features/community/domain/member-directory";
import type {
  CommunityRole,
  Membership,
} from "@/features/community/domain/membership";
import { RoleSwitch } from "@/features/community/ui/RoleSwitch";
import type { MemberStatus, User } from "@/features/identity/domain/user";
import { MemberLabel } from "@/features/identity/ui/MemberLabel";
import { cn } from "@/lib/cn";
import { userHue } from "@/lib/user-hue";

interface MemberRowProps {
  member: Membership;
  user?: User | undefined;
  isYou: boolean;
  isDisabled: boolean;
  errorMessage?: string | undefined;
  onChangeRole: (role: CommunityRole) => void;
}

function statusOf(user?: User): MemberStatus {
  return user?.memberStatus ?? "PRESENT";
}

function secondaryLine(member: Membership, user?: User): string {
  const parts = [user?.department, user?.team].filter((part): part is string =>
    Boolean(part),
  );
  const joined = formatJoinedMonth(member.joinedAt);
  if (joined) parts.push(`joined ${joined}`);
  return parts.join(" · ");
}

export function MemberRow({
  member,
  user,
  isYou,
  isDisabled,
  errorMessage,
  onChangeRole,
}: Readonly<MemberRowProps>) {
  const name = user?.displayName ?? member.userId;
  const meta = secondaryLine(member, user);

  return (
    <li
      className={cn(
        "border-border bg-surface flex flex-col gap-1 rounded-md border px-4 py-3 shadow-sm transition-colors",
        member.role === "ORGANIZER" && "border-l-primary border-l-[3px]",
      )}
    >
      <div className="flex items-center gap-3">
        <MemberAvatar
          name={name}
          hue={userHue(member.userId)}
          subject={member.userId}
          shape="circle"
          src={user?.avatarUrl ?? null}
        />
        <div className="flex min-w-0 flex-col">
          <span className="text-foreground flex items-center gap-2 text-sm font-semibold">
            <MemberLabel name={name} status={statusOf(user)} />
            {isYou && (
              <span className="text-secondary text-[10px] font-bold tracking-wide uppercase">
                Me
              </span>
            )}
          </span>
          {meta && (
            <span className="text-muted-foreground text-xs">{meta}</span>
          )}
        </div>
        <div className="ml-auto">
          <RoleSwitch
            role={member.role}
            label={name}
            isDisabled={isDisabled}
            onChange={onChangeRole}
          />
        </div>
      </div>
      {errorMessage && (
        <p role="alert" className="text-danger pl-12 text-xs">
          {errorMessage}
        </p>
      )}
    </li>
  );
}
