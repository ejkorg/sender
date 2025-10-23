import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { API_BASE_URL } from './app/core/tokens';
import { environment } from './environments/environment';
import { apiBaseUrlInterceptor } from './app/core/api-base-url.interceptor';

// runtime console marker for API base URL (only in development)
if (!environment.production) {
  // eslint-disable-next-line no-console
  console.info('DEV: resolved API_BASE_URL =', environment.apiBaseUrl);
}

bootstrapApplication(App, {
  ...appConfig,
  providers: [
    provideHttpClient(withInterceptors([apiBaseUrlInterceptor])),
    {
      provide: API_BASE_URL,
      useFactory: () => {
        // In development, when the app is served on 0.0.0.0 or a network IP,
        // prefer using the current page hostname so the browser can reach the
        // backend at the host that served the UI (useful when "localhost"
        // wouldn't resolve from other devices on the network).
        try {
          if (!environment.production) {
            const loc = window.location;
            // If the configured dev base uses localhost, swap hostname
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
