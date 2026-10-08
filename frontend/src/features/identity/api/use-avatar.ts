import { useMutation, useQueryClient } from "@tanstack/react-query";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { writeMe } from "@/features/identity/api/write-me";

const gateway = httpIdentityGateway;

export function useSetAvatar() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => gateway.setAvatar(file),
    onSuccess: writeMe(queryClient),
  });
}

export function useClearAvatar() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => gateway.clearAvatar(),
    onSuccess: writeMe(queryClient),
  });
}
