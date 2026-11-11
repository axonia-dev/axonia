import type { Ref } from "react";

import { Avatar } from "@/components/ui/Avatar";
import { useEdition } from "@/edition-context";
import { cn } from "@/lib/cn";

import { OpenRing } from "./OpenRing";

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
  /** Reaches the outermost element drawn, decoration box included. */
  ref?: Ref<HTMLElement>;
  /** Draws the open ring: this person is open to questions on what the surface shows. */
  open?: boolean;
  /** The speech dot beside the ring, dropped at sizes under about 32 px. */
  openDot?: boolean;
  /** Names the ring to a screen reader, on a surface with no other text saying the person is open. */
  openLabelled?: boolean;
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
  open = false,
  openDot = true,
  openLabelled = false,
  ref,
  ...face
}: Readonly<MemberAvatarProps>) {
  if (!open) return <DecoratedAvatar {...face} {...(ref ? { ref } : {})} />;
  return (
    <OpenRing ref={ref} dot={openDot} labelled={openLabelled}>
      <DecoratedAvatar {...face} />
    </OpenRing>
  );
}

function DecoratedAvatar({
  name,
  hue,
  subject,
  src,
  shape = "circle",
  className = "",
  ref,
}: Readonly<Omit<MemberAvatarProps, "open" | "openDot" | "openLabelled">>) {
  const { AvatarDecoration } = useEdition();
  const decorated = AvatarDecoration !== null && subject !== null;
  const avatar = (
    <Avatar
      {...(decorated ? {} : { ref })}
      name={name}
      hue={hue}
      src={src}
      shape={shape}
      className={className}
    />
  );
  if (!decorated) return avatar;
  return (
    <span ref={ref} className={decoratedAvatarClass(shape)}>
      {avatar}
      <AvatarDecoration subject={subject} />
    </span>
  );
}
