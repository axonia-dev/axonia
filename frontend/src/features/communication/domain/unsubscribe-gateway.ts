/** Port for the public, no-session unsubscribe a mail footer link lands on. Resolves on a
 *  204 (whatever `UnsubscribeController` privately did with the token) and rejects with an
 *  {@link ApiError} - see `@/lib/api-client` - otherwise, so the page can read the status
 *  the backend actually gave it. */
export interface UnsubscribeGateway {
  unsubscribe(token: string | null): Promise<void>;
}
