import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { useMe } from "@/features/identity/api/use-me";
import type {
  MentoringRole,
  PairingSummary,
} from "@/features/mentoring/domain/mentoring";
import { PartnerFace } from "@/features/mentoring/ui/PartnerFace";
import { cn } from "@/lib/cn";
import { userHue } from "@/lib/user-hue";

const ROLE_RING: Record<MentoringRole, string> = {
  MENTOR: "ring-mentor",
  MENTEE: "ring-mentee",
};

function partnerRoleOf(myRole: MentoringRole): MentoringRole {
  return myRole === "MENTOR" ? "MENTEE" : "MENTOR";
}
/** The two people, each ringed in the colour of their role, standing where the trail has reached. */
export function Walkers({
  pairing,
  still,
}: Readonly<{ pairing: PairingSummary; still: boolean }>) {
  const me = useMe();
  const bob = still
    ? ""
    : "motion-safe:animate-[walker-bob_1.4s_ease-in-out_infinite]";
  return (
    <span className="flex">
      <span
        className={cn("rounded-full ring-2", ROLE_RING[pairing.myRole], bob)}
      >
        <MemberAvatar
          name={me.data?.displayName ?? ""}
          hue={userHue(me.data?.id ?? "")}
          subject={me.data?.id ?? null}
          src={me.data?.avatarUrl ?? null}
          className="size-7 text-[10px]"
        />
      </span>
      <span
        className={cn(
          "-ml-1.5 rounded-full ring-2 [animation-delay:250ms]",
          ROLE_RING[partnerRoleOf(pairing.myRole)],
          bob,
        )}
      >
        <PartnerFace
          userId={pairing.partnerId}
          name={pairing.partnerName}
          avatarUrl={pairing.partnerAvatarUrl}
          className="size-7 text-[10px]"
        />
      </span>
    </span>
  );
}
