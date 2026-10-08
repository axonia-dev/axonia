import { useMyMembership } from "@/features/community/api/use-membership";

/** UX-only role signal (BE enforces auth). Reads the viewer's own membership
 *  from the /me endpoint, so it stays correct for organizers of communities
 *  past the first page of members. */
export function useIsOrganizer(slug: string): boolean {
  const { data: membership } = useMyMembership(slug);
  return membership?.role === "ORGANIZER";
}
