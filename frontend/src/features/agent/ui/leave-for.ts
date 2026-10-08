/** Hands the browser back to the MCP client. A module of its own so tests can observe it. */
export function leaveFor(url: string): void {
  window.location.assign(url);
}
