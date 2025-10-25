import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { API_BASE_URL } from './app/core/tokens';
import { environment } from './environments/environment';
import { apiBaseUrlInterceptor } from './app/core/api-base-url.interceptor';
import { authInterceptorFn } from './app/core/auth-interceptor.fn';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './app/auth/auth.interceptor';

// runtime console marker for API base URL (only in development)
if (!environment.production) {
  // eslint-disable-next-line no-console
  console.info('DEV: resolved API_BASE_URL =', environment.apiBaseUrl);
}

bootstrapApplication(App, {
  ...appConfig,
  providers: [
    // preserve providers defined in appConfig (routing, global providers)
    ...(appConfig.providers ?? []),
    // app-specific http client with our interceptors
    provideHttpClient(withInterceptors([apiBaseUrlInterceptor, authInterceptorFn])),
    // Keep the class provider for backwards compatibility, but the functional
    // interceptor above is what `provideHttpClient` will run for the new
    // HttpClient pipeline.
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    {
      provide: API_BASE_URL,
      useFactory: () => {
        // If dev proxy is enabled, return empty so interceptor leaves /api
        // requests intact and the dev server proxies them.
        try {
          if (!environment.production && (environment as any).useProxy) {
            return '';
          }
          if (!environment.production) {
            const loc = window.location;
            if (environment.apiBaseUrl && environment.apiBaseUrl.includes('localhost')) {
              const port = environment.apiBaseUrl.split(':').pop() || '8080';
              return `${loc.protocol}//${loc.hostname}:${port}`;
            }
          }
        } catch (e) {
          // ignore and fall back to environment value
        }
        return environment.apiBaseUrl;
      }
    }
  ]
})
  .catch((err) => console.error(err));
