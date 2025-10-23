import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from './tokens';

export const apiBaseUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const base = inject(API_BASE_URL) as string;
  const isAbsolute = /^https?:\/\//i.test(req.url);
  const isApiCall = req.url.startsWith('/api');
  if (!isAbsolute && isApiCall) {
    const normalizedBase = base.replace(/\/+$/, '');
    const normalizedPath = req.url.replace(/^\/+/, '');
    req = req.clone({ url: `${normalizedBase}/${normalizedPath}` });
  }
  return next(req);
};
