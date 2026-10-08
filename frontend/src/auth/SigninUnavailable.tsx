import { SigningIn } from "@/auth/SigningIn";
import { Button } from "@/components/ui/Button";

export const SIGNIN_UNAVAILABLE = "Sign-in is unavailable. Retrying.";

/**
 * The guard's wait, which becomes the page's own state while the identity provider cannot be
 * reached and there is no page to keep. The sign-in it offers is the way out of a wait the
 * provider is not the cause of. One live region for both, so the change is announced.
 */
export function SigningInOrUnavailable({
  unavailable,
  onSignIn,
}: Readonly<{ unavailable: boolean; onSignIn: () => void }>) {
  return (
    <div role="status">
      {unavailable ? (
        <div className="bg-background text-foreground flex min-h-dvh flex-col items-center justify-center gap-4 px-6 text-center">
          <p className="text-muted-foreground">{SIGNIN_UNAVAILABLE}</p>
          <Button onPress={onSignIn}>Sign in</Button>
        </div>
      ) : (
        <SigningIn />
      )}
    </div>
  );
}
