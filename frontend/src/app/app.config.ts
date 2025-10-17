import { ApplicationConfig, importProvidersFrom, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { AuthInterceptor } from './auth/auth.interceptor';
import { NgModule } from '@angular/core';
import { provideRouter } from '@angular/router';
import { appRoutes } from './app';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
  provideHttpClient(withInterceptorsFromDi()),
    importProvidersFrom(FormsModule),
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
    ,
    provideRouter(appRoutes)
  ]
};
