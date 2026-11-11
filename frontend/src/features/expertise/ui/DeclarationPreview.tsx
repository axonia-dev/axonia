import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import type { Tier } from "@/features/expertise/domain/expertise";
import { AskAboutPill } from "@/features/expertise/ui/AskAboutPill";
import { ownIdentity } from "@/features/expertise/ui/own-identity";
import { TierBadge } from "@/features/expertise/ui/TierBadge";
import { useMe } from "@/features/identity/api/use-me";

interface Props {
  tags: string[];
  tier: Tier | null;
  paused: boolean;
}

function Caption() {
  return (
    <p className="text-muted-foreground text-[11px] font-semibold tracking-[0.08em] uppercase">
      As others see it
    </p>
  );
}

/** The member's own search card as others see it while declaring: ring and invitation, or paused.
 *  A paused member with nothing but declarations is in no search at all, so no card stands for them. */
export function DeclarationPreview({ tags, tier, paused }: Readonly<Props>) {
  const me = useMe().data;
  const { name, role, hue, avatarUrl } = ownIdentity(me);
  if (paused && tier === "OPEN_TO_QUESTIONS") {
    return (
      <div className="flex min-w-0 flex-col gap-2">
        <Caption />
        <p className="border-border text-muted-foreground rounded-md border border-dashed p-4 text-sm">
          Hidden from search while paused
        </p>
      </div>
    );
  }
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <Caption />
      <article className="border-border bg-surface rounded-md border p-4">
        <div className="flex items-start gap-3">
          <MemberAvatar
            name={name}
            hue={hue}
            subject={me?.id ?? null}
            src={avatarUrl}
            className="size-10 text-sm"
            open={!paused}
          />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-foreground font-medium">{name}</span>
              {tier !== null && <TierBadge tier={tier} />}
            </div>
            {role !== null && (
              <p className="text-muted-foreground text-sm">{role}</p>
            )}
            {paused ? (
              <p className="text-muted-foreground mt-1 text-xs">Paused</p>
            ) : (
              <div className="mt-2">
                <AskAboutPill term={tags.join(", ")} since={null} />
              </div>
            )}
          </div>
        </div>
      </article>
    </div>
  );
}
