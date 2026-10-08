import {
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { scheduleKeys, sessionKeys } from "@/features/events/api/query-keys";
import type { UpdateSessionInput } from "@/features/events/domain/events-gateway";
import { entryDayIso } from "@/features/events/domain/occurrence-day";
import {
  governingZone,
  wallClockOf,
} from "@/features/events/domain/reschedule-target";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { Session } from "@/features/events/domain/session";

const gateway = httpEventsGateway;

/**
 * Where a dragged occurrence lands: the day it was dropped on, at the hour it already meets, read
 * in the zone that governs it.
 *
 * The hour is carried as a wall clock rather than as a moved instant because the server composes
 * an occurrence of a series in that series' zone: handing it an hour read on anyone else's clock
 * would move a community's session by an offset nobody sees. An occurrence nobody set an hour for
 * stays untimed, which is why `wallClockOf` reads the stored `hasTime` rather than the shape of
 * the instant.
 */
export function movedTo(
  session: Session,
  targetIsoDate: string,
): UpdateSessionInput["scheduledAt"] {
  return {
    date: targetIsoDate,
    time: wallClockOf(session),
    zone: governingZone(session),
  };
}

/** A future, still-open occurrence is the only thing a drag may move. */
export function isDraggableEntry(entry: ScheduleEntry): boolean {
  return entry.status === "SCHEDULED";
}

/** The dnd-kit draggable id for an entry (real session or projected ghost). */
export function dragId(entry: ScheduleEntry): string {
  return entry.sessionId !== null
    ? `s:${entry.sessionId}`
    : `g:${entry.seriesId}:${entry.occurrenceDate}`;
}

interface Drop {
  entry: ScheduleEntry;
  targetIso: string;
}

/** Resolve a drag-end into the move to apply, or null for a no-op (no target / same day). */
export function resolveDrop({ active, over }: DragEndEvent): Drop | null {
  if (!over) return null;
  const entry = active.data.current?.entry as ScheduleEntry | undefined;
  const targetIso = over.data.current?.iso as string | undefined;
  if (!entry || !targetIso) return null;
  if (entryDayIso(entry) === targetIso) return null;
  return { entry, targetIso };
}

/**
 * Gives a projected ghost the row it stands for.
 *
 * Reached from a confirm and from a drop, and from nothing else: opening an editor writes nothing,
 * so an organizer who opens the reschedule dialog on a ghost and then abandons it leaves the
 * projection exactly as it was.
 */
function materialise(entry: ScheduleEntry): Promise<Session> {
  return gateway.ensureOccurrence(
    entry.seriesId as number,
    entry.occurrenceDate as string,
  );
}

/** Both writers move an occurrence, so both make the month grid, the community's session lists and
 *  the occurrence's own detail stale. Stated once so the two cannot refresh different things. */
function useScheduleRefresh(slug: string) {
  const queryClient = useQueryClient();
  return (session: Session) =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: scheduleKeys.all }),
      queryClient.invalidateQueries({ queryKey: sessionKeys.community(slug) }),
      queryClient.invalidateQueries({
        queryKey: sessionKeys.detail(session.id),
      }),
    ]);
}

interface Confirmed {
  entry: ScheduleEntry;
  input: UpdateSessionInput;
}

/**
 * Applies the replacement an organizer confirmed in the reschedule dialog, against the row the
 * chip stands for - materialised first when the calendar was showing a ghost.
 *
 * The input is the caller's and is never rebuilt here. `PUT /sessions/{id}` replaces the session
 * wholesale, and the dialog edits fields this hook has no reading of, so composing a payload from
 * anything but what was confirmed is how a field nobody touched gets cleared.
 */
export function useCalendarReschedule(slug: string) {
  const refresh = useScheduleRefresh(slug);
  return useMutation({
    mutationFn: async ({ entry, input }: Confirmed) => {
      const sessionId = entry.sessionId ?? (await materialise(entry)).id;
      return gateway.updateSession(sessionId, input);
    },
    onSuccess: refresh,
  });
}

/**
 * Reschedules a dragged occurrence. A real session is fetched (to preserve its
 * duration, facilitator and own place, which `PUT /sessions/{id}` fully replaces);
 * a ghost is first materialized. Either way the new `scheduledAt` keeps the original
 * hour on the dropped day; the occurrence anchor is untouched.
 *
 * A drag commits at once rather than opening the dialog: it is a direct gesture that says only
 * which day, and interrupting it with a form to confirm would take away the reason to drag.
 *
 * The place sent back is the override and never the resolved `place`: an
 * occurrence that inherits where it meets must still inherit it after a drag, and one
 * that overrides must keep the override. Posting the resolved place would detach
 * every inheriting occurrence somebody ever dragged.
 */
export function useCalendarDragReschedule(slug: string) {
  const refresh = useScheduleRefresh(slug);
  return useMutation({
    mutationFn: async ({ entry, targetIso }: Drop) => {
      const session =
        entry.sessionId !== null
          ? await gateway.getSession(entry.sessionId)
          : await materialise(entry);
      return gateway.updateSession(session.id, {
        scheduledAt: movedTo(session, targetIso),
        durationMinutes: session.durationMinutes,
        facilitatorUserId: session.facilitatorUserId,
        place: session.placeOverride,
      });
    },
    onSuccess: refresh,
  });
}

interface ProviderProps {
  slug: string;
  children: ReactNode;
}

/**
 * Wraps the calendar grid so organizer drags reschedule an occurrence. Mounted
 * only for organizers; non-organizers render the grid without any drag wiring.
 */
export function CalendarDndProvider({
  slug,
  children,
}: Readonly<ProviderProps>) {
  const reschedule = useCalendarDragReschedule(slug);
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 6 },
    }),
    useSensor(KeyboardSensor),
  );

  return (
    <DndContext
      sensors={sensors}
      onDragEnd={(event) => {
        const drop = resolveDrop(event);
        if (drop) {
          reschedule.mutate(drop);
        }
      }}
    >
      {children}
    </DndContext>
  );
}
