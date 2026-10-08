import { CalendarPlus } from "lucide-react";
import type { ReactNode } from "react";

import {
  AppleMark,
  GoogleMark,
  MicrosoftMark,
} from "@/components/ui/BrandMarks";
import { Button, buttonClass } from "@/components/ui/Button";
import { CopyField } from "@/components/ui/CopyField";
import { DialogFooter } from "@/components/ui/DialogFooter";
import {
  type CalendarSubscription,
  calendarTargets,
  formatLastSync,
  webcalUrl,
} from "@/features/events/domain/calendar-subscription";
import { useNow } from "@/lib/use-wake-at";

const MARK_CLASS = "h-4 w-4 shrink-0";

/** The face of each handover, keyed by the target it belongs to. "Any other" has no vendor and
 *  takes the product's own glyph, because a row of three logos and one gap reads as a mark that
 *  failed to load. */
const MARKS: Record<string, ReactNode> = {
  google: <GoogleMark className={MARK_CLASS} />,
  outlook: <MicrosoftMark className={MARK_CLASS} />,
  apple: <AppleMark className={MARK_CLASS} />,
  other: <CalendarPlus aria-hidden className={MARK_CLASS} />,
};

interface SubscriptionPanelProps {
  subscription: CalendarSubscription;
  name: string;
  onReset: () => void;
  onDone: () => void;
}

/**
 * A subscription that exists, in its two honest forms: the moment its URL is readable, and
 * every moment after. Both carry the sync line once there is one, because that is the only
 * evidence a member ever gets that a link pasted into a calendar actually took.
 */
export function SubscriptionPanel({
  subscription,
  name,
  onReset,
  onDone,
}: Readonly<SubscriptionPanelProps>) {
  const { url } = subscription;
  return (
    <div className="flex flex-col gap-4">
      {url === null ? (
        <LinkAlreadyHandedOver />
      ) : (
        <Handover url={url} name={name} />
      )}
      <SyncLine subscription={subscription} />
      <DialogFooter>
        <Button variant="ghost" onPress={onReset}>
          Reset link
        </Button>
        <Button onPress={onDone}>Done</Button>
      </DialogFooter>
    </div>
  );
}

function Handover({ url, name }: Readonly<{ url: string; name: string }>) {
  const webcal = webcalUrl(url);
  return (
    <>
      {/* Named, because "Outlook" or "Any other" read on their own say nothing of what
          pressing them does. */}
      <ul aria-label="Subscribe from" className="grid grid-cols-2 gap-2">
        {calendarTargets(webcal, name).map((target) => (
          <li key={target.id}>
            <a
              href={target.href}
              className={buttonClass("outline", "sm", "w-full gap-2")}
              // Only the two web calendars open a page. A webcal link is handed to the
              // operating system, and a blank tab would be left behind for nothing.
              {...(target.href.startsWith("https://")
                ? { target: "_blank", rel: "noreferrer" }
                : {})}
            >
              {MARKS[target.id]}
              {target.label}
            </a>
          </li>
        ))}
      </ul>
      <CopyField
        label="Calendar link"
        value={webcal}
        copyLabel="Copy the calendar link"
        hint="Shown once. Reset link mints a new one."
      />
    </>
  );
}

/** Not a failure and styled as none: an existing subscription keeps working, it just cannot
 *  be read out a second time. */
function LinkAlreadyHandedOver() {
  return (
    <div className="bg-surface-sunken rounded-lg px-3.5 py-3">
      <p className="text-foreground text-sm">
        The calendar link was handed over once and only its fingerprint was
        kept, so it cannot be shown again.
      </p>
      <p className="text-muted-foreground mt-2 text-sm">
        Any calendar already holding it keeps syncing. Reset link mints a
        replacement and stops the old one.
      </p>
    </div>
  );
}

/** Present only once there is a sync to report: see {@link formatLastSync}. */
function SyncLine({
  subscription,
}: Readonly<{ subscription: CalendarSubscription }>) {
  const line = formatLastSync(subscription, useNow());
  if (line === null) return null;
  return <p className="text-foreground text-sm">{line}</p>;
}
