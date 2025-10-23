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
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl }
  ]
})
  .catch((err) => console.error(err));
