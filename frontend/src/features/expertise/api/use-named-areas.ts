import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { notify, notifyError } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { namedAreaErrorMessage } from "@/features/expertise/curation-errors";
import type { NamedArea } from "@/features/expertise/domain/named-area";

const gateway = httpExpertiseGateway;

/** The stored list, including tags the map cannot draw. */
export function useNamedAreas(slug: string) {
  return useQuery({
    queryKey: expertiseKeys.namedAreas(slug),
    queryFn: () => gateway.namedAreas(slug),
  });
}

function failed(fallback: string) {
  return (error: unknown) =>
    notifyError(namedAreaErrorMessage(error, fallback));
}

async function createHolding(slug: string, name: string, tags: string[]) {
  const created = await gateway.createNamedArea(slug, name);
  const placements = await Promise.allSettled(
    tags.map((tag) => gateway.placeInNamedArea(slug, created.id, tag)),
  );
  const allPlaced = placements.every((p) => p.status === "fulfilled");
  return { created, allPlaced };
}

function reportCreated({
  created,
  allPlaced,
}: {
  created: NamedArea;
  allPlaced: boolean;
}) {
  if (allPlaced) notify(`Created ${created.name}`);
  else notifyError(`Created ${created.name}, but some tags were not placed.`);
}

/** Exact on the map: its contributions sit under the same prefix, and a named area moves none of them. */
export function useNamedAreaActions(slug: string) {
  const queryClient = useQueryClient();
  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: expertiseKeys.map(slug),
        exact: true,
      }),
      queryClient.invalidateQueries({
        queryKey: expertiseKeys.namedAreas(slug),
      }),
    ]);

  const create = useMutation({
    mutationFn: ({ name, tags }: { name: string; tags: string[] }) =>
      createHolding(slug, name, tags),
    onSuccess: reportCreated,
    onSettled: invalidate,
    onError: failed("Couldn’t create that named area."),
  });

  const rename = useMutation({
    mutationFn: ({ id, name }: { id: number; name: string }) =>
      gateway.renameNamedArea(slug, id, name),
    onSuccess: (_data, { name }) => notify(`Renamed to ${name}`),
    onSettled: invalidate,
    onError: failed("Couldn’t rename that named area."),
  });

  const remove = useMutation({
    mutationFn: (namedArea: NamedArea) =>
      gateway.deleteNamedArea(slug, namedArea.id),
    onSuccess: (_data, namedArea) => notify(`Deleted ${namedArea.name}`),
    onSettled: invalidate,
    onError: failed("Couldn’t delete that named area."),
  });

  const place = useMutation({
    mutationFn: ({ id, tag }: { id: number; tag: string }) =>
      gateway.placeInNamedArea(slug, id, tag),
    onSettled: invalidate,
    onError: failed("Couldn’t place that tag."),
  });

  const takeOut = useMutation({
    mutationFn: (tag: string) => gateway.removeFromNamedArea(slug, tag),
    onSettled: invalidate,
    onError: failed("Couldn’t take that tag out."),
  });

  return {
    createNamedArea: (name: string, tags: string[] = []) =>
      create.mutate({ name, tags }),
    renameNamedArea: (id: number, name: string) => rename.mutate({ id, name }),
    deleteNamedArea: (namedArea: NamedArea) => remove.mutate(namedArea),
    placeInNamedArea: (id: number, tag: string) => place.mutate({ id, tag }),
    removeFromNamedArea: (tag: string) => takeOut.mutate(tag),
  };
}
