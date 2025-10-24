import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from './tokens';

export const apiBaseUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const base = inject(API_BASE_URL) as string | undefined;
  const isAbsolute = /^https?:\/\//i.test(req.url);
  const isApiCall = req.url.startsWith('/api');

  // If no base provided (or base is empty), assume dev proxy is in use and
  // leave the request URL as-is so the dev server can proxy it.
  if (!isAbsolute && isApiCall && base) {
    const normalizedBase = base.replace(/\/+$/, '');
    const normalizedPath = req.url.replace(/^\/+/, '');
    req = req.clone({ url: `${normalizedBase}/${normalizedPath}` });
  }
  return next(req);
};
