export interface CeremonyClock {
  now(): number;
  request(callback: () => void): number;
  cancel(handle: number): void;
}

export const browserClock: CeremonyClock = {
  now: () => performance.now(),
  request: (callback) => requestAnimationFrame(callback),
  cancel: (handle) => cancelAnimationFrame(handle),
};
