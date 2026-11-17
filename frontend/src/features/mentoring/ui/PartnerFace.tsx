import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { userHue } from "@/lib/user-hue";

const DELETED = "Deleted member";

export function personName(name: string, userId: string | null = ""): string {
  return userId === null || name.trim() === "" ? DELETED : name;
}

interface Props {
  userId: string | null;
  name: string;
  avatarUrl: string | null;
  className?: string;
}

export function PartnerFace({
  userId,
  name,
  avatarUrl,
  className = "size-9 text-xs",
}: Readonly<Props>) {
  return (
    <MemberAvatar
      name={personName(name)}
      hue={userHue(userId ?? "")}
      subject={userId}
      src={avatarUrl}
      className={className}
    />
  );
}
