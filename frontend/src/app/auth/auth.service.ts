import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { HttpClient, HttpHeaders } from '@angular/common/http';

export interface UserInfo {
  username: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private userSubject = new BehaviorSubject<UserInfo | null>(null);
  user$ = this.userSubject.asObservable();
  private basicTokenKey = 'reloader_basic_token';

  constructor(private http: HttpClient) {
    const token = localStorage.getItem(this.basicTokenKey);
    if (token) {
      // set a minimal user to indicate authenticated state
      const decoded = atob(token.split(' ')[1] || '');
      const username = decoded.split(':')[0] || '';
      this.userSubject.next({ username });
    }
  }

  login(username: string, password: string): Observable<any> {
    // Use Basic auth to call a harmless backend endpoint to verify credentials.
    const token = 'Basic ' + btoa(`${username}:${password}`);
    const headers = new HttpHeaders({ Authorization: token });
    return new Observable(observer => {
      this.http.get('/api/auth/test', { headers, responseType: 'text' }).subscribe(
        _ => {
          localStorage.setItem(this.basicTokenKey, token);
          this.userSubject.next({ username });
          observer.next(true);
          observer.complete();
        },
        err => {
          observer.error(err);
        }
      );
    });
  }

  logout() {
    localStorage.removeItem(this.basicTokenKey);
    this.userSubject.next(null);
  }

  getAuthHeaders(): HttpHeaders | null {
    const token = localStorage.getItem(this.basicTokenKey);
    if (!token) return null;
    return new HttpHeaders({ Authorization: token });
  }
}
