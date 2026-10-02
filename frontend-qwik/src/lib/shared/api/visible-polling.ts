/** A page owns one bounded request; hidden/disposed pages cancel it. False stops polling. */
export function startVisiblePolling(update: (signal: AbortSignal) => Promise<boolean>): () => void {
  let request: AbortController | undefined;
  let running = false;
  let stopped = false;
  let lastStarted = 0;
  const stop = () => {
    stopped = true;
    clearInterval(timer);
    request?.abort();
    document.removeEventListener("visibilitychange", visibility);
  };
  const tick = async () => {
    if (stopped || running || document.visibilityState !== "visible" || Date.now() - lastStarted < 60_000) return;
    running = true;
    lastStarted = Date.now();
    request = new AbortController();
    try { if (!await update(request.signal)) stop(); }
    catch { /* Page displays its own sanitized error; polling can recover. */ }
    finally { running = false; }
  };
  const visibility = () => {
    if (document.visibilityState !== "visible") request?.abort();
    else void tick();
  };
  const timer = setInterval(() => { void tick(); }, 60_000);
  document.addEventListener("visibilitychange", visibility);
  void tick();
  return stop;
}
