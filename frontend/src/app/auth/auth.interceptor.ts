import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, from } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private auth: AuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const headers = this.auth.getAuthHeaders();
    const authReq = headers ? req.clone({ setHeaders: { Authorization: headers.get('Authorization') || '' } }) : req;

    return next.handle(authReq).pipe(
      catchError((err: any) => {
        if (err instanceof HttpErrorResponse && err.status === 401) {
          // attempt refresh
          return this.auth.refresh().pipe(
            switchMap((ok: boolean) => {
              if (!ok) return throwError(() => err);
              const newHeaders = this.auth.getAuthHeaders();
              const retried = authReq.clone({ setHeaders: { Authorization: newHeaders ? newHeaders.get('Authorization') || '' : '' } });
              return next.handle(retried);
            })
          );
        }
        return throwError(() => err);
      })
    );
  }
}
