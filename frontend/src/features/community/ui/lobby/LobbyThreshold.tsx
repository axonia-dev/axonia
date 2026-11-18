import { X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import {
  Button as AriaButton,
  Dialog,
  Heading,
  Modal,
  ModalOverlay,
} from "react-aria-components";

import { MorphLink } from "@/components/patterns/morph/MorphLink";
import { MorphPart } from "@/components/patterns/morph/MorphPart";
import { Avatar } from "@/components/ui/Avatar";
import { useSeedCommunityDetail } from "@/features/community/api/seed-community-detail";
import type { LobbyItem } from "@/features/community/domain/lobby";
import {
  accentVariables,
  communityHue,
} from "@/features/community/ui/community-accent";
import { useLobbyMorphId } from "@/features/community/ui/lobby/lobby-morph";
import {
  LatelyInside,
  NextSession,
  Stat,
  WhoIsHere,
} from "@/features/community/ui/lobby/lobby-threshold-sections";
import { OpenLink } from "@/features/community/ui/lobby/LobbyBillboardSlide";
import { LobbyArt } from "@/features/community/ui/lobby/LobbyCard";
import { LobbyJoinButton } from "@/features/community/ui/lobby/LobbyJoinButton";
import { useLobbyFaces } from "@/features/community/ui/lobby/use-lobby-faces";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";
import { useNow } from "@/lib/use-wake-at";

const BIG = "w-full py-3 text-base";

function PeekLink({ item }: Readonly<{ item: LobbyItem }>) {
  const seed = useSeedCommunityDetail();
  return (
    <MorphLink
      to={`/communities/${item.slug}`}
      ready={() => seed(item)}
      className="text-muted-foreground hover:text-foreground focus-visible:ring-focus-ring self-center rounded-sm text-sm underline underline-offset-4 outline-none focus-visible:ring-2"
    >
      Peek inside
    </MorphLink>
  );
}

const DRIFT: Keyframe[] = [
  { transform: "scale(1.04) translate(0, 0)" },
  { transform: "scale(1.12) translate(-14px, -8px)" },
];

/** The hero drifts slowly, as if the room behind the door were alive; still under reduced motion. */
function DriftingArt({ item }: Readonly<{ item: LobbyItem }>) {
  const layer = useRef<HTMLSpanElement>(null);
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  useEffect(() => {
    const element = layer.current;
    if (reduced || element === null || typeof element.animate !== "function") {
      return undefined;
    }
    const drift = element.animate(DRIFT, {
      duration: 18_000,
      easing: "ease-in-out",
      direction: "alternate",
      iterations: Infinity,
    });
    return () => drift.cancel();
  }, [reduced]);
  return (
    <span ref={layer} data-drift className="absolute inset-0">
      <LobbyArt item={item} />
    </span>
  );
}

function ThresholdBody({
  item,
  close,
}: Readonly<{ item: LobbyItem; close: () => void }>) {
  const faces = useLobbyFaces([item]);
  const now = useNow();
  const joined = item.role !== null;
  const morph = useLobbyMorphId(item.slug, { kind: "threshold" });
  return (
    <>
      <div className="relative h-56 flex-none overflow-hidden sm:h-80">
        <MorphPart part="banner" id={morph}>
          <DriftingArt item={item} />
        </MorphPart>
        <span
          aria-hidden
          className="to-surface absolute inset-0 bg-linear-to-b from-transparent from-30%"
        />
        <AriaButton
          aria-label="Close"
          onPress={close}
          className="focus-visible:ring-focus-ring absolute top-4 right-4 flex size-9 cursor-pointer items-center justify-center rounded-full bg-black/55 text-white backdrop-blur-md outline-none hover:bg-black/70 focus-visible:ring-2"
        >
          <X size={18} aria-hidden />
        </AriaButton>
      </div>
      <div className="relative grid grid-cols-1 gap-8 px-6 pb-8 sm:px-10 md:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] md:gap-10">
        <div className="flex min-w-0 flex-col gap-6">
          <div className="-mt-14 flex items-end gap-4">
            <MorphPart part="badge" id={morph}>
              <Avatar
                name={item.name}
                hue={communityHue(item)}
                src={item.badgeUrl ?? null}
                className="border-surface size-20 flex-none rounded-2xl border-4 text-2xl shadow-lg sm:size-24"
              />
            </MorphPart>
            <div className="flex min-w-0 flex-col gap-1 pb-1">
              <span className="text-primary-ink text-xs font-bold tracking-widest uppercase">
                {item.kind}
              </span>
              <MorphPart part="name" id={morph}>
                <Heading
                  slot="title"
                  className="font-display text-foreground text-3xl leading-tight font-bold tracking-tight sm:text-4xl"
                >
                  {item.name}
                </Heading>
              </MorphPart>
            </div>
          </div>
          {item.description.trim() !== "" && (
            <p className="text-muted-foreground text-base leading-relaxed">
              {item.description}
            </p>
          )}
          <div className="flex gap-8">
            <Stat value={item.memberCount} noun="member" />
            <Stat value={item.stats.sessionsHeld} noun="session" verb="held" />
            <Stat
              value={item.stats.artifactsShared}
              noun="thing"
              verb="shared"
            />
          </div>
          <LatelyInside item={item} now={now} />
        </div>
        <div className="flex min-w-0 flex-col gap-6 md:pt-6">
          <WhoIsHere item={item} faces={faces} />
          <NextSession item={item} now={now} />
          <div className="flex flex-col gap-3">
            {joined ? (
              <OpenLink item={item} className={BIG} />
            ) : (
              <LobbyJoinButton
                slug={item.slug}
                size="md"
                fullWidth
                className={BIG}
              />
            )}
            {!joined && <PeekLink item={item} />}
          </div>
        </div>
      </div>
    </>
  );
}

/**
 * A step inside a community without entering it: what it is, who is there, what happened lately
 * and what comes next, with Join as the way in. A full-screen sheet on a phone.
 */
export function LobbyThreshold({
  item,
  onClose,
}: Readonly<{
  item: LobbyItem | null;
  onClose: () => void;
}>) {
  const [shown, setShown] = useState(item);
  if (item !== null && item !== shown) setShown(item);

  return (
    <ModalOverlay
      isOpen={item !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
      isDismissable
      className="fixed inset-0 z-50 flex items-stretch justify-center bg-black/70 backdrop-blur-sm transition-opacity duration-200 data-[entering]:opacity-0 data-[exiting]:opacity-0 sm:items-center sm:p-6 md:p-10"
    >
      <Modal className="flex w-full max-w-5xl motion-safe:transition-transform motion-safe:duration-300 motion-safe:ease-out motion-safe:data-[entering]:translate-y-10 motion-safe:data-[entering]:scale-95 motion-safe:data-[exiting]:translate-y-10 sm:max-h-full">
        {shown !== null && (
          <Dialog
            style={accentVariables(shown)}
            className={cn(
              "bg-surface relative flex w-full flex-col overflow-y-auto outline-none",
              "sm:border-border sm:rounded-2xl sm:border sm:shadow-2xl",
            )}
          >
            {({ close }) => <ThresholdBody item={shown} close={close} />}
          </Dialog>
        )}
      </Modal>
    </ModalOverlay>
  );
}
