import { Avatar } from "@/components/ui/Avatar";
import { useEdition } from "@/edition-context";
import { cn } from "@/lib/cn";

type Shape = "square" | "circle";

interface MemberAvatarProps {
  name: string;
  hue: string;
  /** Whose avatar this is, handed to the edition's decoration, and `null` where no person can be
   *  named. Required, like `src`, so the next surface cannot leave its people undecorated by
   *  omission. */
  subject: string | null;
  /** Required, and `null` for a person with no picture, rather than optional: the one call site
   *  that simply left it out drew a monogram for somebody who has a photo. */
  src: string | null;
  shape?: Shape;
  /** Applied to the avatar itself, exactly as on a bare `Avatar`: size, text size, borders. */
  className?: string;
}

/** The positioned box a decoration fills, following the avatar's corners. */
export function decoratedAvatarClass(shape: Shape): string {
  return cn(
    "relative inline-flex shrink-0",
    shape === "circle" ? "rounded-full" : "rounded-lg",
  );
}

/** The avatar every surface that renders a person draws, decorated by the running edition. */
export function MemberAvatar({
  name,
  hue,
  subject,
  src,
  shape = "circle",
  className = "",
}: Readonly<MemberAvatarProps>) {
  const { AvatarDecoration } = useEdition();
  const avatar = (
    <Avatar
      name={name}
      hue={hue}
      src={src}
      shape={shape}
      className={className}
    />
  );
  if (AvatarDecoration === null || subject === null) return avatar;
  return (
    <span className={decoratedAvatarClass(shape)}>
      {avatar}
      <AvatarDecoration subject={subject} />
    </span>
  );
}
