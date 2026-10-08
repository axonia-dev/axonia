// The authenticated subject (JWT `sub`), kept current by the auth guard. A
// module-level seam rather than a hook: consumers compare ids during render,
// and the value only changes across full login/logout redirects.

let userId: string | undefined;

export function setCurrentUserId(id: string | undefined): void {
  userId = id;
}

export function currentUserId(): string | undefined {
  return userId;
}
