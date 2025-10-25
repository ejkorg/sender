import { HttpInterceptorFn } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { AuthService } from '../auth/auth.service';

// Functional interceptor that mirrors the class-based AuthInterceptor behavior
// but is compatible with the `provideHttpClient(withInterceptors(...))` API.
export const authInterceptorFn: HttpInterceptorFn = (req, next) => {
  const injector = inject(Injector);
  // lazily resolve AuthService to avoid circular DI (AuthService -> HttpClient -> interceptors)
  const authServiceProvider = () => injector.get(AuthService);

  // Bypass auth endpoints (login/refresh/logout)
  if (req.url.includes('/api/auth/login') || req.url.includes('/api/auth/refresh') || req.url.includes('/api/auth/logout')) {
    return next(req);
  }

  const token = authServiceProvider().getAccessToken();
  if (!token) return next(req);

  if (req.headers.has('Authorization')) return next(req);

  const masked = token && token.length ? (token.length > 12 ? token.substring(0, 6) + '…' + token.substring(token.length - 4) : token) : 'null';
  try { console.debug('[authInterceptorFn] adding Authorization header - token=', masked, ' url=', req.url); } catch (e) { }

  const authReq = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  return next(authReq);
};
