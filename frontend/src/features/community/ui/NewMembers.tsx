import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import { SectionCard } from "@/components/ui/SectionCard";
import { useMembers } from "@/features/community/api/use-membership";
import { useUsers } from "@/features/identity/api/use-users";
import { startOfMonthIso } from "@/lib/period";
import { useNow } from "@/lib/use-wake-at";
import { userHue } from "@/lib/user-hue";

/**
 * Welcomes members who joined this month — a celebratory cluster, never a
 * ranking of members against each other (see the recognition safeguard).
 */
export function NewMembers({ slug }: Readonly<{ slug: string }>) {
  const monthStart = startOfMonthIso(useNow());
  const members = useMembers(slug, { page: 0, size: 100 });
  const recent = (members.data?.content ?? [])
    .filter((member) => Date.parse(member.joinedAt) >= Date.parse(monthStart))
    .sort((a, b) => Date.parse(b.joinedAt) - Date.parse(a.joinedAt));
  const users = useUsers(recent.map((member) => member.userId));

  if (members.isPending || recent.length === 0) {
    return null;
  }

  const directory = new Map(Object.entries(users.data ?? {}));
  const nameOf = (userId: string) =>
    directory.get(userId)?.displayName ?? "Someone";
  const avatarOf = (userId: string) => directory.get(userId)?.avatarUrl ?? null;
  const newest = recent[0];

  return (
    <SectionCard title="New members" icon="hand" accent="var(--success)">
      <div className="flex flex-col gap-3 px-2 pt-1 pb-1">
        <MemberFacepile
          members={recent.map((member) => ({
            id: member.userId,
            name: nameOf(member.userId),
            hue: userHue(member.userId),
            src: avatarOf(member.userId),
          }))}
          size="size-9 motion-safe:animate-[avatar-pop_0.3s_ease]"
        />
        <div>
          <p className="text-foreground text-sm font-semibold">
            {newest ? nameOf(newest.userId) : "Someone"} just joined
          </p>
          <p className="text-muted-foreground text-xs">
            +{recent.length} new this month
          </p>
        </div>
      </div>
    </SectionCard>
  );
}
