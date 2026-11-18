import {
  type QueryKey,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";

/** A mutation that refreshes the given keys once it settles: a refusal (an ended pairing, a closed proposal) means the shown state is stale too. */
export function useRefreshing<TVariables, TResult>(
  mutationFn: (variables: TVariables) => Promise<TResult>,
  keys: (variables: TVariables) => QueryKey[],
  onSuccess?: (result: TResult) => void,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    ...(onSuccess === undefined ? {} : { onSuccess }),
    onSettled: (_result, _error, variables) =>
      Promise.all(
        keys(variables).map((queryKey) =>
          queryClient.invalidateQueries({ queryKey }),
        ),
      ),
  });
}
