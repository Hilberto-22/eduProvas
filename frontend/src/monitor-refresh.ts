/** Coalesces bursts, serializes requests and preserves one trailing refresh. */
export class MonitorRefresh {
  private timer?: ReturnType<typeof setTimeout>;
  private running?: Promise<void>;
  private pending = false;
  private stopped = false;

  constructor(private readonly refresh: () => Promise<void>, private readonly delay = 500) {}

  schedule() {
    if (this.stopped) return;
    this.pending = true;
    if (this.timer || this.running) return;
    this.timer = setTimeout(() => {
      this.timer = undefined;
      void this.flush().catch(() => {});
    }, this.delay);
  }

  async flush(): Promise<void> {
    if (this.stopped) return;
    if (this.timer) { clearTimeout(this.timer); this.timer = undefined; }
    if (this.running) { this.pending = true; return this.running; }
    this.pending = false;
    this.running = this.refresh();
    try { await this.running; }
    finally {
      this.running = undefined;
      if (this.pending) this.schedule();
    }
  }

  stop() {
    this.stopped = true;
    this.pending = false;
    clearTimeout(this.timer);
    this.timer = undefined;
  }
}
