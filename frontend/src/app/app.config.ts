import { ApplicationConfig, ErrorHandler } from "@angular/core";
import { provideHttpClient, withInterceptors } from "@angular/common/http";
import { provideRouter } from "@angular/router";
import { APP_ROUTES } from "./app.routes";
import { authInterceptor } from "./core/interceptors/auth.interceptor";
import { apiErrorInterceptor } from "./core/interceptors/api-error.interceptor";
import { AppErrorHandler } from "./core/errors/app-error-handler";

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(APP_ROUTES),
    provideHttpClient(withInterceptors([authInterceptor, apiErrorInterceptor])),
    { provide: ErrorHandler, useClass: AppErrorHandler },
  ],
};
