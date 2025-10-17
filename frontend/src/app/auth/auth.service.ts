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
    const token = this.getAccessToken();
    const username = token ? this.extractUsername(token) : null;
    if (token && username) {
      this.userSubject.next({ username });
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

  private extractUsername(token: string): string | null {
    if (!token) {
      return null;
    }
    if (token.includes('.')) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        if (payload && payload.sub) {
          return String(payload.sub);
        }
      } catch (e) {
        return null;
      }
    } else if (token.startsWith('token:')) {
      const parts = token.split(':');
      if (parts.length >= 2) {
        return parts[1];
      }
    }
    return null;
  }

  private setSession(token: string | null, fallbackUsername?: string): void {
    if (!token) {
      localStorage.removeItem(this.accessTokenKey);
      this.userSubject.next(null);
      return;
    }
    localStorage.setItem(this.accessTokenKey, token);
    const username = this.extractUsername(token) || fallbackUsername || null;
    if (username) {
      this.userSubject.next({ username });
    } else {
      this.userSubject.next(null);
    }
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.accessTokenKey);
  }

  // returns true if token exists and will expire within `ttlSeconds` seconds
  isTokenExpiringWithin(ttlSeconds: number): boolean {
    const token = this.getAccessToken();
    if (!token) return true;
    const exp = this.parseExpiry(token);
    if (!exp) return true;
    const nowSec = Math.floor(Date.now() / 1000);
    return (exp - nowSec) <= ttlSeconds;
  }

  // refresh the access token using the backend refresh endpoint (backend sets refresh cookie)
  refresh(): Observable<boolean> {
    return this.http.post('/api/auth/refresh', null, { withCredentials: true }).pipe(
      map((res: any) => {
        const token = res && res.accessToken ? res.accessToken : null;
        if (token) {
          this.setSession(token);
          return true;
        }
        this.setSession(null);
        return false;
      }),
      catchError((_) => of(false))
    );
  }

  // Use the proper JWT login endpoint
  login(username: string, password: string): Observable<boolean> {
    return new Observable<boolean>((observer) => {
      this.http.post('/api/auth/login', { username, password }, { withCredentials: true }).subscribe(
        (res: any) => {
          const token = res && res.accessToken ? res.accessToken : null;
          if (token) {
            this.setSession(token, username);
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
    this.setSession(null);
  }

  getAuthHeaders(): HttpHeaders | null {
    const token = this.getAccessToken();
    if (!token) return null;
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
