import { Check, ChevronDown, Search } from "lucide-react";
import { useState } from "react";

import {
  MenuAnswerSection,
  MenuButton,
  MenuHeading,
  MenuItem,
  MenuSeparator,
} from "@/components/ui/Menu";
import { statusChipClass } from "@/components/ui/StatusChip";
import {
  instantToZoned,
  type ZonedParts,
} from "@/features/events/domain/zoned-instant";
import type { ReleaseAnswerSet } from "@/features/events/ui/release-answers";
import type { SessionRelease } from "@/features/events/ui/release-chip";
import { formatReleaseInstant } from "@/features/events/ui/release-copy";
import { ReleasePreviewDialog } from "@/features/events/ui/ReleasePreviewDialog";
import { ReleaseScheduleDialog } from "@/features/events/ui/ReleaseScheduleDialog";
import { viewerZone } from "@/lib/zones";

/** No instant chosen yet, which is what a suppressed message opens the picker on. */
const NOTHING_PICKED: ZonedParts = { date: "", time: "" };

interface Props {
  slug: string;
  sessionId: number;
  release: SessionRelease;
  answers: ReleaseAnswerSet;
}

interface OptionProps {
  label: string;
  hint: string;
}

/**
 * One answer's two lines. The checkmark is for sighted readers only: the surrounding
 * `MenuAnswerSection` renders each item as `menuitemradio`/`aria-checked`, so the standing answer
 * already reaches assistive tech as a semantic state rather than as this glyph.
 */
function AnswerOption({ label, hint }: Readonly<OptionProps>) {
  return (
    <>
      <span aria-hidden className="text-primary w-3.5 shrink-0">
        <Check className="hidden size-3.5 group-aria-checked:block" />
      </span>
      <span className="flex min-w-0 flex-col">
        <span className="truncate">{label}</span>
        <span className="text-muted-foreground text-xs">{hint}</span>
      </span>
    </>
  );
}

/**
 * Where this session's outgoing mail is stated, answered and previewed - all of it, in one place.
 *
 * One chip in the facts strip carries the state in as few words as it takes, and opens onto the
 * fact in full: when the message goes out, to how many, the three mutually exclusive answers while
 * any are still open, and the preview. Nothing about the release is stated anywhere else on the
 * page, which is the whole reason this exists: the state used to reach a reader twice, once here
 * and again in a banner past the foot of the run sheet, with only the banner able to act on it.
 *
 * Whether there is a release to state at all, and whether this reader may be told it, is settled
 * before this renders - see `sessionRelease` and the caller's own `canManageMessages`.
 */
export function SessionReleaseChip({
  slug,
  sessionId,
  release,
  answers,
}: Readonly<Props>) {
  const [previewOpen, setPreviewOpen] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [scheduleValue, setScheduleValue] =
    useState<ZonedParts>(NOTHING_PICKED);
  const { chip, copy, preview } = release;
  const { actions, releaseAt, latestReleaseAt } = answers;
  const suppressed = releaseAt === null;

  // Read on the viewer's own clock, which is the clock a release is picked on: no community zone
  // governs when a mail goes out.
  function openSchedule() {
    actions.reset();
    setScheduleValue(
      releaseAt === null
        ? NOTHING_PICKED
        : instantToZoned(releaseAt, viewerZone()),
    );
    setScheduleOpen(true);
  }

  return (
    <>
      <MenuButton
        label={chip.label}
        className={statusChipClass(chip.tone)}
        trigger={
          <>
            <span
              aria-hidden
              className="size-1.5 flex-none rounded-full bg-current"
            />
            {chip.label}
            <ChevronDown aria-hidden className="size-3 flex-none opacity-70" />
          </>
        }
        onAction={(key) => {
          if (key === "now") actions.sendNow();
          if (key === "schedule") openSchedule();
          if (key === "off") actions.suppress();
          if (key === "preview") setPreviewOpen(true);
        }}
      >
        <MenuHeading statement={copy.statement} subline={copy.subline} />
        {release.answerable && (
          <MenuAnswerSection
            label={answers.copy.note}
            selectedKey={suppressed ? "off" : "schedule"}
          >
            <MenuItem id="now" isDisabled={actions.isPending}>
              <AnswerOption
                label="Send it now"
                hint="Goes out within a minute"
              />
            </MenuItem>
            <MenuItem id="schedule" isDisabled={actions.isPending}>
              <AnswerOption
                label={
                  releaseAt === null
                    ? "Pick a time"
                    : formatReleaseInstant(releaseAt)
                }
                hint={
                  suppressed
                    ? "Send it at a specific time"
                    : "Pick a different time"
                }
              />
            </MenuItem>
            <MenuItem id="off" isDisabled={actions.isPending || suppressed}>
              <AnswerOption
                label="Do not send it"
                hint={answers.copy.offHint}
              />
            </MenuItem>
          </MenuAnswerSection>
        )}
        {chip.previewable && (
          <>
            {release.answerable && <MenuSeparator />}
            {/* "it" rather than the message's noun: the heading directly above has just named
                the message, and it puts this item in the same voice as the answers beside it. */}
            <MenuItem id="preview">
              <Search aria-hidden className="size-3.5 shrink-0" />
              Preview it
            </MenuItem>
          </>
        )}
      </MenuButton>
      <ReleaseScheduleDialog
        title={answers.copy.dialogTitle}
        fieldLabel={answers.copy.fieldLabel}
        isOpen={scheduleOpen}
        onOpenChange={setScheduleOpen}
        value={scheduleValue}
        onValueChange={setScheduleValue}
        hasCurrentSchedule={!suppressed}
        onConfirm={(picked) =>
          actions.scheduleAt(picked, () => setScheduleOpen(false))
        }
        isPending={actions.isPending}
        {...(latestReleaseAt === undefined ? {} : { latestReleaseAt })}
      />
      <ReleasePreviewDialog
        slug={slug}
        sessionId={sessionId}
        copy={preview}
        isOpen={previewOpen}
        onOpenChange={setPreviewOpen}
      />
    </>
  );
}
