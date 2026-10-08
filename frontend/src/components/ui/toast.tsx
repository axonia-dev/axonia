import {
  Button,
  Text,
  UNSTABLE_Toast as Toast,
  UNSTABLE_ToastContent as ToastContent,
  UNSTABLE_ToastQueue as ToastQueue,
  UNSTABLE_ToastRegion as ToastRegion,
} from "react-aria-components";

export interface ToastAction {
  label: string;
  onPress: () => void;
}

interface ToastBody {
  title: string;
  tone: "neutral" | "error";
  action?: ToastAction;
}

export const toastQueue = new ToastQueue<ToastBody>({ maxVisibleToasts: 4 });

/** Raise a transient failure notice (6s timeout — past the 5s a11y minimum). */
export function notifyError(title: string): void {
  toastQueue.add({ title, tone: "error" }, { timeout: 6000 });
}

/** Raise a neutral notice, optionally with a single action (e.g. Undo). */
export function notify(title: string, action?: ToastAction): void {
  toastQueue.add(
    { title, tone: "neutral", ...(action ? { action } : {}) },
    { timeout: 6000 },
  );
}

const toneClass: Record<ToastBody["tone"], string> = {
  neutral: "bg-surface border-border text-foreground",
  error: "bg-surface border-danger/40 text-danger",
};

export function ToastViewport() {
  return (
    // Below md the shells fix a tab bar to the bottom edge and keep their content pb-24 clear of
    // it, so a toast sits at the same height rather than over the navigation.
    <ToastRegion
      queue={toastQueue}
      className="fixed right-4 bottom-24 z-50 flex flex-col gap-2 outline-none md:bottom-4"
    >
      {({ toast }) => (
        <Toast
          toast={toast}
          className={`${toneClass[toast.content.tone]} flex items-center gap-3 rounded-md border px-4 py-3 text-sm shadow-lg data-[entering]:animate-[hovercard-in_180ms_ease-out]`}
        >
          <ToastContent className="flex-1">
            <Text slot="title">{toast.content.title}</Text>
          </ToastContent>
          {toast.content.action ? (
            <Button
              onPress={() => {
                toast.content.action?.onPress();
                toastQueue.close(toast.key);
              }}
              className="text-primary-strong hover:text-primary data-[focus-visible]:ring-focus-ring cursor-pointer rounded-sm px-1 font-semibold outline-none data-[focus-visible]:ring-2"
            >
              {toast.content.action.label}
            </Button>
          ) : null}
          <Button
            slot="close"
            aria-label="Dismiss"
            className="text-muted-foreground hover:text-foreground data-[focus-visible]:ring-focus-ring cursor-pointer rounded-sm px-1 leading-none outline-none data-[focus-visible]:ring-2"
          >
            ✕
          </Button>
        </Toast>
      )}
    </ToastRegion>
  );
}
