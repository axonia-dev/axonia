import type { FacepileMember } from "@/components/patterns/MemberFacepile";
import {
  BirthCeremony,
  type CeremonyCardProps,
} from "@/features/community/ui/ceremony/BirthCeremony";
import type { ActiveCeremony } from "@/features/community/ui/ceremony/use-hero-ceremony";
import { WelcomeCeremony } from "@/features/community/ui/ceremony/WelcomeCeremony";
import { useMe } from "@/features/identity/api/use-me";
import type { User } from "@/features/identity/domain/user";
import { userHue } from "@/lib/user-hue";

export interface HeroCeremonyProps extends Omit<CeremonyCardProps, "clock"> {
  ceremony: ActiveCeremony;
}

function faceOf(me: User): FacepileMember {
  return {
    id: me.id,
    name: me.displayName,
    hue: userHue(me.id),
    src: me.avatarUrl ?? null,
  };
}

/** The lazily loaded entry point: the ceremony the community hero plays right now. */
export function HeroCeremony({
  ceremony,
  ...card
}: Readonly<HeroCeremonyProps>) {
  const { data: me } = useMe();
  if (ceremony.kind === "birth") {
    return <BirthCeremony {...card} />;
  }
  return (
    <WelcomeCeremony
      {...card}
      newcomer={me === undefined ? null : faceOf(me)}
      countFrom={ceremony.countFrom}
      countTo={ceremony.countTo}
    />
  );
}
