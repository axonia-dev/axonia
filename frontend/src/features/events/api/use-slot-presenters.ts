import { useMutation } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useInvalidateSession } from "@/features/events/api/use-invalidate-session";
import type { RecordSlotInput } from "@/features/events/domain/slot-gateway";

const gateway = httpEventsGateway;

export function useRecordSlot(slug: string, sessionId: number) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (vars: { sectionId: number; input: RecordSlotInput }) =>
      gateway.recordSlot(sessionId, vars.sectionId, vars.input),
    onSuccess: invalidate,
  });
}

export function useAddPresenter(slug: string, sessionId: number) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (vars: {
      sectionId: number;
      slotId: number;
      presenter: string;
    }) =>
      gateway.addPresenter(
        sessionId,
        vars.sectionId,
        vars.slotId,
        vars.presenter,
      ),
    onSuccess: invalidate,
  });
}

export function useRemovePresenter(slug: string, sessionId: number) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (vars: { sectionId: number; slotId: number; userId: string }) =>
      gateway.removePresenter(
        sessionId,
        vars.sectionId,
        vars.slotId,
        vars.userId,
      ),
    onSuccess: invalidate,
  });
}
