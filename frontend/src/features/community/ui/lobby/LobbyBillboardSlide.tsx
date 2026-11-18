import { CalendarDays } from "lucide-react";
import { Link } from "react-router-dom";

import { MemberFacepile } from "@/components/patterns/MemberFacepile";
import { MorphLink } from "@/components/patterns/morph/MorphLink";
import { MorphPart } from "@/components/patterns/morph/MorphPart";
import { Button, buttonClass } from "@/components/ui/Button";
import { useSeedCommunityDetail } from "@/features/community/api/seed-community-detail";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { accentVariables } from "@/features/community/ui/community-accent";
import {
  CREATE_HERO,
  lobbyEyebrow,
  nextSessionLine,
} from "@/features/community/ui/lobby/lobby-copy";
import {
  useLobbyMorphId,
  useShowSlide,
} from "@/features/community/ui/lobby/lobby-morph";
import { LiveDot, LobbyArt } from "@/features/community/ui/lobby/LobbyCard";
import { LobbyJoinButton } from "@/features/community/ui/lobby/LobbyJoinButton";
import type { LobbyFaces } from "@/features/community/ui/lobby/use-lobby-faces";
import { cn } from "@/lib/cn";
import { plural } from "@/lib/plural";
import { useNow } from "@/lib/use-wake-at";

const SLIDE_FACES = 5;

/** The billboard's frame: full bleed, as tall as the prototype's stage on a desk, taller than wide on a phone. */
const STAGE =
  "relative isolate h-128 overflow-hidden bg-black text-white md:h-115";

/** On a desk the copy lines up with the lobby's column below, however wide the billboard runs. */
const COPY =
  "absolute inset-x-0 bottom-0 flex flex-col items-start p-6 md:right-auto md:bottom-10 md:left-[max(2.5rem,calc(50%-36rem))] md:max-w-[min(32rem,calc(100%-22rem))] md:p-0";

const BIG = "px-6 py-3 text-base";

const GLASS =
  "border border-white/20 bg-white/10 text-white backdrop-blur-md hover:bg-white/20";

/** Into a community the person already belongs to, through the shared-element morph. */
export function OpenLink({
  item,
  className = BIG,
}: Readonly<{ item: LobbyItem; className?: string }>) {
  const seed = useSeedCommunityDetail();
  return (
    <MorphLink
      to={`/communities/${item.slug}`}
      ready={() => seed(item)}
      className={buttonClass("primary", "md", className)}
    >
      Open
    </MorphLink>
  );
}

/** One featured community, large, over its own hero: why it is featured, who is there, what is next. */
export function LobbyBillboardSlide({
  item,
  active,
  faces,
  onOpenThreshold,
}: Readonly<{
  item: LobbyItem;
  active: boolean;
  faces: LobbyFaces;
  onOpenThreshold: (item: LobbyItem) => void;
}>) {
  const now = useNow();
  const joined = item.role !== null;
  useShowSlide(item.slug, active);
  const morph = useLobbyMorphId(item.slug, active ? { kind: "slide" } : null);
  return (
    <div style={accentVariables(item)} className={STAGE}>
      <MorphPart part="banner" id={morph}>
        <LobbyArt
          item={item}
          className={cn(
            "motion-safe:transition-transform motion-safe:duration-[9s] motion-safe:ease-linear",
            active ? "scale-100" : "motion-safe:scale-106",
          )}
        />
      </MorphPart>
      <span
        aria-hidden
        className="absolute inset-0 bg-linear-to-t from-black/95 via-black/60 to-black/5 md:bg-linear-to-r md:from-black/90 md:via-black/55 md:to-transparent"
      />
      <div
        className={cn(
          COPY,
          "transition-opacity duration-300 ease-out motion-safe:transition-[opacity,translate] motion-safe:duration-500",
          active ? "opacity-100" : "opacity-0 motion-safe:translate-x-8",
        )}
      >
        <span className="text-xs font-bold tracking-widest text-[color-mix(in_srgb,var(--community-accent)_45%,white)] uppercase">
          {lobbyEyebrow(item)}
        </span>
        <MorphPart part="name" id={morph}>
          <h2 className="font-display mt-3 text-4xl leading-none font-bold tracking-tight md:text-5xl">
            {item.name}
          </h2>
        </MorphPart>
        {item.description.trim() !== "" && (
          <p className="mt-3 line-clamp-2 text-sm text-white/80 md:text-base">
            {item.description}
          </p>
        )}
        <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-white/85">
          <span className="flex items-center gap-2">
            <MemberFacepile
              members={faces(item, SLIDE_FACES)}
              max={SLIDE_FACES}
            />
            {plural(item.memberCount, "member")}
          </span>
          <span className="flex min-w-0 items-center gap-2">
            {item.nextSessionThisWeek ? (
              <LiveDot />
            ) : (
              <CalendarDays size={16} aria-hidden className="flex-none" />
            )}
            {nextSessionLine(item, now)}
          </span>
        </div>
        <div className="mt-6 flex flex-wrap items-start gap-3">
          {joined ? (
            <OpenLink item={item} />
          ) : (
            <LobbyJoinButton
              slug={item.slug}
              size="md"
              onImage
              className={BIG}
            />
          )}
          <Button
            variant="ghost"
            onPress={() => onOpenThreshold(item)}
            className={cn(BIG, GLASS)}
          >
            Peek inside
          </Button>
        </div>
      </div>
    </div>
  );
}

/** An organization with no community yet: the billboard's slot invites founding the first one,
 *  or, for a person who cannot found one, simply says so. */
export function LobbyCreateHero({
  canCreate,
}: Readonly<{ canCreate: boolean }>) {
  const title = canCreate ? CREATE_HERO.title : CREATE_HERO.absent;
  return (
    <section
      aria-label={title}
      className={cn(
        STAGE,
        "bg-[radial-gradient(60rem_30rem_at_10%_0%,color-mix(in_srgb,var(--primary)_55%,black),black_70%)]",
      )}
    >
      <div className={COPY}>
        {canCreate && (
          <span className="mb-3 text-xs font-bold tracking-widest text-[color-mix(in_srgb,var(--primary)_45%,white)] uppercase">
            {CREATE_HERO.eyebrow}
          </span>
        )}
        <h2 className="font-display text-4xl leading-none font-bold tracking-tight md:text-5xl">
          {title}
        </h2>
        <p className="mt-3 text-sm text-white/80 md:text-base">
          {CREATE_HERO.body}
        </p>
        {canCreate && (
          <Link
            to="/communities/new"
            className={buttonClass("primary", "md", cn("mt-6", BIG))}
          >
            {CREATE_HERO.action}
          </Link>
        )}
      </div>
    </section>
  );
}
