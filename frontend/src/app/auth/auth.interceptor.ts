import { Injectable } from '@angular/core';
import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { Observable, BehaviorSubject, throwError } from 'rxjs';
import { catchError, filter, switchMap, take } from 'rxjs/operators';
import { AuthService } from './auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private refreshInProgress = false;
  private refreshSubject = new BehaviorSubject<string | null>(null);

  constructor(private auth: AuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (this.shouldBypass(req.url)) {
      return next.handle(req);
    }

    const token = this.auth.getAccessToken();
    const authReq = token ? this.addAuthorization(req, token) : req;

    return next.handle(authReq).pipe(
      catchError(err => {
        if (err instanceof HttpErrorResponse && err.status === 401) {
          return this.handleUnauthorized(authReq, next);
        }
        return throwError(() => err);
      })
    );
  }

  private handleUnauthorized(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (this.refreshInProgress) {
      return this.refreshSubject.pipe(
        filter(token => token !== null),
        take(1),
        switchMap(token => next.handle(this.addAuthorization(request, token!)))
      );
    }

    this.refreshInProgress = true;
    this.refreshSubject.next(null);

    return this.auth.refresh().pipe(
      switchMap(success => {
        this.refreshInProgress = false;
        const token = success ? this.auth.getAccessToken() : null;
        if (token) {
          this.refreshSubject.next(token);
          return next.handle(this.addAuthorization(request, token));
        }
        this.refreshSubject.next(null);
        this.auth.logout();
        return throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }));
      }),
      catchError(error => {
        this.refreshInProgress = false;
        this.refreshSubject.next(null);
        this.auth.logout();
        return throwError(() => error);
      })
    );
  }

  private addAuthorization(request: HttpRequest<any>, token: string): HttpRequest<any> {
    if (request.headers.has('Authorization')) {
      return request;
    }
    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  private shouldBypass(url: string): boolean {
    return url.includes('/api/auth/login') || url.includes('/api/auth/refresh') || url.includes('/api/auth/logout');
  }
}
