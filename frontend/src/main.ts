import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { API_BASE_URL } from './app/core/tokens';
import { environment } from './environments/environment';
import { apiBaseUrlInterceptor } from './app/core/api-base-url.interceptor';

bootstrapApplication(App, {
  ...appConfig,
  providers: [
    provideHttpClient(withInterceptors([apiBaseUrlInterceptor])),
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl }
  ]
})
  .catch((err) => console.error(err));
