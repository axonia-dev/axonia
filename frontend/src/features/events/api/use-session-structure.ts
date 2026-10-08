import { useMutation } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useInvalidateSession } from "@/features/events/api/use-invalidate-session";
import type { SectionTemplateInput } from "@/features/events/domain/events-gateway";

const gateway = httpEventsGateway;

export function useAddSection(slug: string, sessionId: number) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (input: SectionTemplateInput) =>
      gateway.addSection(sessionId, input),
    onSuccess: invalidate,
  });
}

export function useUpdateSection(slug: string, sessionId: number) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: ({
      sectionId,
      input,
    }: {
      sectionId: number;
      input: SectionTemplateInput;
    }) => gateway.updateSection(sessionId, sectionId, input),
    onSuccess: invalidate,
  });
}

export function useRemoveSection(slug: string, sessionId: number) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (sectionId: number) =>
      gateway.removeSection(sessionId, sectionId),
    onSuccess: invalidate,
  });
}

export function useReorderSections(slug: string, sessionId: number) {
  const invalidate = useInvalidateSession(slug, sessionId);
  return useMutation({
    mutationFn: (sectionIds: number[]) =>
      gateway.reorderSections(sessionId, sectionIds),
    onSuccess: invalidate,
  });
}
