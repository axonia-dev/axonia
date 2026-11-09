import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import type { User } from "@/features/identity/domain/user";
import { userHue } from "@/lib/user-hue";

interface Props {
  userId: string;
  name: string;
  user: User | undefined;
}

/** Who created the topic, drawn as the library card draws a contributor. */
export function TopicByline({ userId, name, user }: Readonly<Props>) {
  return (
    <span className="text-muted-foreground flex min-w-0 items-center gap-1 text-xs">
      <span>by</span>
      <MemberAvatar
        name={name}
        hue={userHue(userId)}
        subject={userId}
        src={user?.avatarUrl ?? null}
        shape="circle"
        className="size-5 rounded-full text-[0.6rem]"
      />
      <span className="min-w-0 truncate">{name}</span>
    </span>
  );
}
