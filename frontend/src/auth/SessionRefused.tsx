import { Button } from "@/components/ui/Button";

export const SESSION_REFUSED =
  "Signed in, but this server refuses the session. An administrator can check the server's log.";

/**
 * The page's own state when the server refuses the token a sign-in brought back moments ago, in
 * place of a sign-in that would only come back refused again. The sign-in it offers is the
 * person's choice, for once the server has been put right.
 */
export function SessionRefused({
  onSignIn,
}: Readonly<{ onSignIn: () => void }>) {
  return (
    <div
      role="alert"
      className="bg-background text-foreground flex min-h-dvh flex-col items-center justify-center gap-4 px-6 text-center"
    >
      <p className="text-muted-foreground">{SESSION_REFUSED}</p>
      <Button onPress={onSignIn}>Sign in</Button>
    </div>
  );
}
