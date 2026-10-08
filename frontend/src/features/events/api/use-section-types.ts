import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { sectionTypeKeys } from "@/features/events/api/query-keys";
import type { SectionTypeInput } from "@/features/events/domain/events-gateway";

const gateway = httpEventsGateway;

export function useSectionTypes() {
  return useQuery({
    queryKey: sectionTypeKeys.list(),
    queryFn: () => gateway.listSectionTypes(),
  });
}

export function useCreateSectionType() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: SectionTypeInput) => gateway.createSectionType(input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: sectionTypeKeys.all }),
  });
}

export function useUpdateSectionType() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: SectionTypeInput }) =>
      gateway.updateSectionType(id, input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: sectionTypeKeys.all }),
  });
}

export function useDeleteSectionType() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reassignToId }: { id: number; reassignToId?: number }) =>
      gateway.deleteSectionType(id, reassignToId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: sectionTypeKeys.all }),
  });
}
