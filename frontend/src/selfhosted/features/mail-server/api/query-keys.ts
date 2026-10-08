/** Every read of the mail server sits under one root, so a clear refreshes them all. */
export const mailServerKeys = {
  all: ["mail-server"] as const,
  view: () => [...mailServerKeys.all, "view"] as const,
  status: () => [...mailServerKeys.all, "status"] as const,
  changes: () => [...mailServerKeys.all, "changes"] as const,
};
