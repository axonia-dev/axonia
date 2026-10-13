import { Suspense, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";

import { StageBackdrop } from "@/components/patterns/ceremony/StageBackdrop";
import { Button } from "@/components/ui/Button";
import { useCommunity } from "@/features/community/api/use-communities";
import {
  useAddAsset,
  useRemoveAsset,
  useSetAccent,
  useSetFocalPoint,
} from "@/features/community/api/use-look-and-feel";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  LazyWelcomePreview,
  preloadWelcomePreview,
} from "@/features/community/ui/ceremony/lazy-ceremonies";
import { useCommunityCrumbs } from "@/features/community/ui/community-chrome";
import { CommunityHeroCard } from "@/features/community/ui/CommunityHeroCard";
import { FocalPointControl } from "@/features/community/ui/FocalPointControl";
import {
  AccentControl,
  BadgeEditZone,
  BannerEditZone,
} from "@/features/community/ui/look-and-feel-controls";
import { OrganizerPage } from "@/features/community/ui/OrganizerPage";

export function LookAndFeelEditor() {
  const [previewing, setPreviewing] = useState(false);
  const card = useRef<HTMLDivElement>(null);
  useEffect(preloadWelcomePreview, []);
  const { slug = "" } = useParams();
  const access = useManagementAccess(slug);
  const query = useCommunity(slug);
  const setAccent = useSetAccent(slug);
  const addAsset = useAddAsset(slug);
  const removeAsset = useRemoveAsset(slug);
  const setFocalPoint = useSetFocalPoint(slug);
  useCommunityCrumbs([{ label: "Look & feel" }]);

  return (
    <OrganizerPage
      isOrganizer={access.lookAndFeel}
      query={query}
      errorText="Could not load this community."
    >
      {(community) => {
        const busy =
          addAsset.isPending ||
          removeAsset.isPending ||
          setFocalPoint.isPending;
        return (
          <section className="flex flex-col gap-4">
            <header className="flex items-start justify-between gap-4">
              <div className="flex flex-col gap-1">
                <h1 className="text-foreground font-display text-xl font-semibold">
                  Look &amp; feel
                </h1>
                <p className="text-muted-foreground text-sm">
                  Give this community a face. Changes apply immediately.
                </p>
              </div>
              <Button
                variant="secondary"
                size="sm"
                onPress={() => setPreviewing(true)}
              >
                Preview welcome
              </Button>
            </header>
            <div ref={card}>
              <CommunityHeroCard
                community={community}
                editing
                bannerOverlay={
                  <BannerEditZone
                    hasImage={community.heroUrl != null}
                    busy={busy}
                    onAdd={(file) => addAsset.mutate({ asset: "hero", file })}
                    onRemove={() => removeAsset.mutate("hero")}
                    reposition={
                      community.heroUrl != null && (
                        <FocalPointControl
                          value={community.heroFocus}
                          isDisabled={busy}
                          onCommit={(focus) =>
                            setFocalPoint.mutateAsync({ asset: "hero", focus })
                          }
                        />
                      )
                    }
                  />
                }
                badgeOverlay={
                  <BadgeEditZone
                    hasImage={community.badgeUrl != null}
                    busy={busy}
                    onAdd={(file) => addAsset.mutate({ asset: "badge", file })}
                    onRemove={() => removeAsset.mutate("badge")}
                    reposition={
                      community.badgeUrl != null && (
                        <FocalPointControl
                          value={community.badgeFocus}
                          isDisabled={busy}
                          onCommit={(focus) =>
                            setFocalPoint.mutateAsync({ asset: "badge", focus })
                          }
                        />
                      )
                    }
                  />
                }
                cornerControl={
                  <div className="absolute right-4 bottom-4">
                    <AccentControl
                      value={community.accentColor}
                      onChange={(value) => setAccent.mutate(value)}
                    />
                  </div>
                }
                footer={
                  <p className="text-muted-foreground text-xs">
                    Banner: up to 5&nbsp;MB · Logo: up to 2&nbsp;MB · PNG, JPEG,
                    WebP
                  </p>
                }
              />
            </div>
            {previewing && (
              <StageBackdrop>
                <Suspense fallback={null}>
                  <LazyWelcomePreview
                    community={community}
                    target={card}
                    onDone={() => setPreviewing(false)}
                  />
                </Suspense>
              </StageBackdrop>
            )}
          </section>
        );
      }}
    </OrganizerPage>
  );
}
