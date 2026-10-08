import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

const STORAGE_KEY = "axonia-sidebar-collapsed";

export function readStoredCollapsed(stored: string | null): boolean {
  return stored === "true";
}

interface SidebarContextValue {
  collapsed: boolean;
  toggle: () => void;
  setCollapsed: (next: boolean) => void;
}

const SidebarContext = createContext<SidebarContextValue | null>(null);

export function SidebarProvider({
  children,
}: Readonly<{ children: ReactNode }>) {
  const [collapsed, setCollapsedState] = useState<boolean>(() =>
    readStoredCollapsed(localStorage.getItem(STORAGE_KEY)),
  );

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, String(collapsed));
  }, [collapsed]);

  const setCollapsed = useCallback((next: boolean) => {
    setCollapsedState(next);
  }, []);

  const toggle = useCallback(() => {
    setCollapsedState((current) => !current);
  }, []);

  const value = useMemo<SidebarContextValue>(
    () => ({ collapsed, toggle, setCollapsed }),
    [collapsed, toggle, setCollapsed],
  );

  return <SidebarContext value={value}>{children}</SidebarContext>;
}

export function useSidebar(): SidebarContextValue {
  const context = useContext(SidebarContext);
  if (context === null) {
    throw new Error("useSidebar must be used within a SidebarProvider");
  }
  return context;
}
