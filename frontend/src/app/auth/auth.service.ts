import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { map, catchError } from 'rxjs/operators';
import { HttpHeaders } from '@angular/common/http';

export interface UserInfo {
  username: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private userSubject = new BehaviorSubject<UserInfo | null>(null);
  user$ = this.userSubject.asObservable();
  private accessTokenKey = 'reloader_access_token';

  constructor(private http: HttpClient) {
    const token = localStorage.getItem(this.accessTokenKey);
    if (token) {
      // set a minimal user to indicate authenticated state (don't trust JWT parsing for dev)
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const username = payload && payload.sub ? payload.sub : '';
        this.userSubject.next({ username });
      } catch (e) {
        this.userSubject.next(null);
      }
    }
  }

  // parse expiry (exp) from JWT payload (seconds since epoch)
  private parseExpiry(token: string): number | null {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload && payload.exp) return payload.exp as number;
    } catch (e) {
      return null;
    }
    return null;
  }

  // returns true if token exists and will expire within `ttlSeconds` seconds
  isTokenExpiringWithin(ttlSeconds: number): boolean {
    const token = localStorage.getItem(this.accessTokenKey);
    if (!token) return true;
    const exp = this.parseExpiry(token);
    if (!exp) return true;
    const nowSec = Math.floor(Date.now() / 1000);
    return (exp - nowSec) <= ttlSeconds;
  }

  // refresh the access token using the backend refresh endpoint (backend sets refresh cookie)
  refresh(): Observable<boolean> {
    return this.http.post('/api/auth/refresh', null).pipe(
      map((res: any) => {
        const token = res && res.accessToken ? res.accessToken : null;
        if (token) {
          localStorage.setItem(this.accessTokenKey, token);
          // update user subject
          try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            const username = payload && payload.sub ? payload.sub : '';
            this.userSubject.next({ username });
          } catch (e) {}
          return true;
        }
        return false;
      }),
      catchError((_) => of(false))
    );
  }

  // Use the proper JWT login endpoint
  login(username: string, password: string): Observable<boolean> {
    return new Observable<boolean>((observer) => {
      this.http.post('/api/auth/login', { username, password }).subscribe(
        (res: any) => {
          const token = res && res.accessToken ? res.accessToken : null;
          if (token) {
            localStorage.setItem(this.accessTokenKey, token);
            this.userSubject.next({ username });
            observer.next(true);
            observer.complete();
          } else {
            observer.error(new Error('No access token in response'));
          }
        },
        (err: any) => observer.error(err)
      );
    });
  }

  logout() {
    localStorage.removeItem(this.accessTokenKey);
    this.userSubject.next(null);
  }

  getAuthHeaders(): HttpHeaders | null {
    const token = localStorage.getItem(this.accessTokenKey);
    if (!token) return null;
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
