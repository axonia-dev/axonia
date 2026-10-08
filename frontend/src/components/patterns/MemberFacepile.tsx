import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { cn } from "@/lib/cn";

export interface FacepileMember {
  id: string;
  name: string;
  hue: string;
  src: string | null;
}

interface MemberFacepileProps {
  members: readonly FacepileMember[];
  /** How many faces to draw before stopping. Defaults to 5. */
  max?: number;
  /** Class names applied to each avatar: a Tailwind size class such as "size-9", and
   *  optionally more classes alongside it, e.g. an entrance animation for a freshly
   *  joined member. */
  size?: string;
}

const DEFAULT_MAX = 5;

/**
 * A cluster of members, capped and overlapping. Never a ranking: the order is the caller's and
 * the cap is a layout bound, so no member is drawn as standing above another.
 *
 * The overlap sits on the wrapper rather than on the avatar, because a decoration is drawn against
 * the avatar's own box and a negative margin inside would slide the avatar out of it.
 */
export function MemberFacepile({
  members,
  max = DEFAULT_MAX,
  size = "size-7 text-[0.625rem]",
}: Readonly<MemberFacepileProps>) {
  if (members.length === 0) {
    return null;
  }

  return (
    <span className="flex">
      {members.slice(0, max).map((member, index) => (
        <span
          key={member.id}
          data-facepile-item
          className={cn("inline-flex", index > 0 && "-ml-2")}
        >
          <MemberAvatar
            name={member.name}
            hue={member.hue}
            subject={member.id}
            src={member.src}
            shape="circle"
            className={cn("border-surface border-2", size)}
          />
        </span>
      ))}
    </span>
  );
}
