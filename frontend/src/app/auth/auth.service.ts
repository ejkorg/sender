import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, Subscription, timer, of, throwError } from 'rxjs';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { catchError, finalize, map, tap } from 'rxjs/operators';

export interface UserInfo {
  username: string;
  roles?: string[];
}

interface LoginResponse {
  accessToken: string;
}

interface RefreshResponse {
  accessToken?: string;
}

interface RegisterResponse {
  message: string;
  verificationToken?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private userSubject = new BehaviorSubject<UserInfo | null>(null);
  user$ = this.userSubject.asObservable();
  // access token kept in-memory to reduce XSS exposure
  private accessToken: string | null = null;
  private refreshTimerSub: Subscription | null = null;
  private readonly baseUrl = '/api/auth';

  constructor(private http: HttpClient) {
    // On startup try to refresh (if refresh cookie present) and then fetch current user info
    this.refresh().subscribe({
      next: success => {
        if (success) {
          this.getMe().subscribe();
        }
      },
      error: () => {
        this.setSession(null);
      }
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
    // Debug: log masked token presence for troubleshooting auth flow
    try {
      const masked = token && token.length ? (token.length > 12 ? token.substring(0, 6) + '…' + token.substring(token.length - 4) : token) : 'null';
      console.debug('[AuthService] setSession - token=', masked);
    } catch (e) { /* ignore logging errors */ }
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

  // Get current authenticated user from backend
  getMe(): Observable<UserInfo | null> {
    // Ensure Authorization header is included on this initial /me call using the
    // in-memory access token as a fallback in case the interceptor hasn't yet
    // picked it up (avoids a race or DI-instance mismatch).
    const headers = this.getAuthHeaders() || undefined;
    return this.http.get<any>(`${this.baseUrl}/me`, { withCredentials: true, headers }).pipe(
      map(res => {
        if (res && res.username) {
          const info: UserInfo = { username: res.username, roles: res.roles || [] };
          this.userSubject.next(info);
          return info;
        }
        return null;
      }),
      catchError(_ => of(null))
    );
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
    return this.http.post<RefreshResponse>(`${this.baseUrl}/refresh`, null, { withCredentials: true }).pipe(
      map(res => {
        const token = res && res.accessToken ? res.accessToken : null;
        if (token) {
          this.setSession(token);
          return true;
        }
        this.setSession(null);
        return false;
      }),
      catchError(() => {
        this.setSession(null);
        return of(false);
      })
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
  login(username: string, password: string): Observable<void> {
    console.debug('[AuthService] login attempt', { username });
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, { username, password }, { withCredentials: true }).pipe(
      tap(res => {
        if (!res?.accessToken) {
          throw new Error('No access token in response');
        }
        this.setSession(res.accessToken, username);
        this.getMe().subscribe();
        console.debug('[AuthService] login succeeded');
      }),
      map(() => void 0),
      catchError(err => {
        console.error('[AuthService] login failed', err);
        return throwError(() => err);
      })
    );
  }

  // Register a new user using backend register endpoint. Return verification token (dev) if provided.
  register(username: string, email: string | null, password: string): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(
      `${this.baseUrl}/register`,
      { username, email, password },
      { withCredentials: true }
    );
  }

  verifyAccount(token: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/verify`, { token }, { withCredentials: true }).pipe(
      map(() => void 0)
    );
  }

  requestPasswordReset(identifier: string): Observable<{ resetToken?: string }> {
    return this.http.post<{ resetToken?: string }>(
      `${this.baseUrl}/request-reset`,
      { username: identifier },
      { withCredentials: true }
    );
  }

  resetPassword(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/reset-password`, { token, password }, { withCredentials: true }).pipe(
      map(() => void 0)
    );
  }

  logout(): void {
    this.http.post<void>(`${this.baseUrl}/logout`, null, { withCredentials: true }).pipe(
      catchError(() => of(void 0)),
      finalize(() => this.setSession(null))
    ).subscribe();
  }

  getAuthHeaders(): HttpHeaders | null {
    const token = this.getAccessToken();
    if (!token) return null;
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
