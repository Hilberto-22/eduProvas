import { InjectionToken } from "@angular/core";

export const API_URL = new InjectionToken<string>("API_URL", {
  providedIn: "root",
  factory: () => "/api",
});
export const HTTP_TIMEOUT_MS = 15_000;
