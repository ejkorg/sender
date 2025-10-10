import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export interface StageStatus {
  site: string;
  senderId: number;
  total: number;
  ready: number;
  sent: number;
  failed: number;
}

export interface ReloadRequest {
  site: string;
  senderId: string;
  startDate?: string;
  endDate?: string;
  testerType?: string;
  dataType?: string;
}

@Injectable({ providedIn: 'root' })
export class BackendService {
  private base = '/api';

  constructor(private http: HttpClient, private auth: AuthService) {}

  listSites(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/sites`);
  }

  reload(request: ReloadRequest): Observable<string> {
    const headers = this.auth.getAuthHeaders();
    return this.http.post(`${this.base}/reload`, request, { responseType: 'text', headers: headers || undefined });
  }

  getStageStatus(): Observable<StageStatus[]> {
    return this.http.get<StageStatus[]>(`${this.base}/stage/status`);
  }
}
