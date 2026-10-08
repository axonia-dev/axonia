import { useMutation } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useInvalidateSession } from "@/features/events/api/use-invalidate-session";
import type {
  EditSlotInput,
  SlotInput,
} from "@/features/events/domain/slot-gateway";

const gateway = httpEventsGateway;

export function useAddSlot(slug: string, sessionId: number, sectionId: number) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (input: SlotInput) =>
      gateway.addSlot(sessionId, sectionId, input),
    onSuccess: invalidate,
  });
}

export function useReorderSlots(
  slug: string,
  sessionId: number,
  sectionId: number,
) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (slotIds: number[]) =>
      gateway.reorderSlots(sessionId, sectionId, slotIds),
    onSuccess: invalidate,
  });
}

export function usePresentSlot(
  slug: string,
  sessionId: number,
  sectionId: number,
  slotId: number,
) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: () => gateway.presentSlot(sessionId, sectionId, slotId),
    onSuccess: invalidate,
  });
}

export function useWithdrawSlot(
  slug: string,
  sessionId: number,
  sectionId: number,
  slotId: number,
) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: () => gateway.withdrawSlot(sessionId, sectionId, slotId),
    onSuccess: invalidate,
  });
}

export function useEditSlot(
  slug: string,
  sessionId: number,
  sectionId: number,
  slotId: number,
) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (input: EditSlotInput) =>
      gateway.editSlot(sessionId, sectionId, slotId, input),
    onSuccess: invalidate,
  });
}

export function useRemoveSlot(
  slug: string,
  sessionId: number,
  sectionId: number,
  slotId: number,
) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: () => gateway.removeSlot(sessionId, sectionId, slotId),
    onSuccess: invalidate,
  });
}
