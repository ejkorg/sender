import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of, Subscription, timer } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { map, catchError } from 'rxjs/operators';
import { HttpHeaders } from '@angular/common/http';

export interface UserInfo {
  username: string;
  roles?: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private userSubject = new BehaviorSubject<UserInfo | null>(null);
  user$ = this.userSubject.asObservable();
  // access token kept in-memory to reduce XSS exposure
  private accessToken: string | null = null;
  private refreshTimerSub: Subscription | null = null;

  constructor(private http: HttpClient) {
    // On startup try to refresh (if refresh cookie present) to establish session
    this.refresh().subscribe(success => {
      // refresh() will populate session via setSession
    });
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
      this.accessToken = null;
      this.cancelScheduledRefresh();
      this.userSubject.next(null);
      return;
    }
    this.accessToken = token;
    const username = this.extractUsername(token) || fallbackUsername || null;
    let roles: string[] = [];
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload && payload.roles) roles = Array.isArray(payload.roles) ? payload.roles : [payload.roles];
    } catch {}
    if (username) this.userSubject.next({ username, roles }); else this.userSubject.next(null);
    this.scheduleRefreshForToken(token);
  }

  isAdmin(): boolean {
    const user = this.userSubject.value;
    return !!user && Array.isArray(user.roles) && user.roles.includes('ROLE_ADMIN');
  }

  getAccessToken(): string | null {
    return this.accessToken;
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

  private scheduleRefreshForToken(token: string) {
    this.cancelScheduledRefresh();
    const exp = this.parseExpiry(token);
    if (!exp) return;
    const now = Math.floor(Date.now() / 1000);
    // schedule refresh 30 seconds before expiry or immediately if already near
    let refreshAt = Math.max(exp - 30, now + 1);
    const millis = (refreshAt - now) * 1000;
    this.refreshTimerSub = timer(millis).subscribe(() => {
      this.refresh().subscribe();
    });
  }

  private cancelScheduledRefresh() {
    if (this.refreshTimerSub) {
      this.refreshTimerSub.unsubscribe();
      this.refreshTimerSub = null;
    }
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

  // Register a new user using backend register endpoint. On success, attempt login.
  register(username: string, email: string | null, password: string): Observable<boolean> {
    return new Observable<boolean>((observer) => {
      this.http.post('/api/auth/register', { username, email, password }, { withCredentials: true }).subscribe(
        (res: any) => {
          // after registration, attempt login to obtain access token
          this.login(username, password).subscribe(
            ok => { observer.next(ok); observer.complete(); },
            err => observer.error(err)
          );
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
