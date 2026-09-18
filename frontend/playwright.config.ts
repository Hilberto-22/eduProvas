import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/browser",
  fullyParallel: false,
  workers: 1,
  use: {
    baseURL: process.env["E2E_BASE_URL"] || "http://127.0.0.1:4300",
    channel: process.env["E2E_BROWSER_CHANNEL"] || undefined,
    trace: "retain-on-failure",
  },
  webServer: process.env["E2E_BASE_URL"]
    ? undefined
    : {
        command: "npm start -- --port 4300",
        url: "http://127.0.0.1:4300",
        reuseExistingServer: !process.env["CI"],
        timeout: 120_000,
      },
});
