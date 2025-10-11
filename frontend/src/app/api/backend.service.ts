import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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
  location?: string;
  testPhase?: string;
}

export interface ExternalEnvironment {
  id?: number;
  name: string;
  description?: string;
}

export interface ExternalLocationSummary {
  id?: number;
  label: string;
  site?: string;
  environmentId?: number;
  dbConnectionName?: string;
  details?: string;
}

export interface ExternalInstance {
  key: string;
  label: string;
  environment: string;
}

export interface EnqueueRequest {
  senderId: number | null;
  payloadIds: string[];
  source?: string;
}

export interface EnqueueResponse {
  enqueuedCount?: number;
  enqueued?: number;
  skippedPayloads?: string[];
  skipped?: string[];
}

export interface ReloadFilterOptions {
  locations: string[];
  dataTypes: string[];
  testerTypes: string[];
  dataTypeExt?: string[];
  fileTypes?: string[];
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

  getReloadFilters(site: string): Observable<ReloadFilterOptions> {
    const params = new HttpParams().set('site', site);
    return this.http.get<ReloadFilterOptions>(`${this.base}/reload/filters`, { params });
  }

  listEnvironments(): Observable<ExternalEnvironment[]> {
    return this.http.get<ExternalEnvironment[]>(`${this.base}/environments`);
  }

  listInstances(environment: string): Observable<ExternalInstance[]> {
    const params = new HttpParams().set('environment', environment);
    return this.http.get<ExternalInstance[]>(`${this.base}/external/instances`, { params });
  }

  listLocations(environment: string): Observable<ExternalLocationSummary[]> {
    return this.http.get<ExternalLocationSummary[]>(`${this.base}/environments/${encodeURIComponent(environment)}/locations`);
  }

  discover(senderId: number, params: Record<string, any>): Observable<string> {
    let httpParams = new HttpParams();
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    });
    const headers = this.auth.getAuthHeaders();
    return this.http.post(`${this.base}/senders/${senderId}/discover`, null, {
      responseType: 'text',
      headers: headers || undefined,
      params: httpParams
    });
  }

  getDistinctLocations(params: Record<string, any>): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/senders/external/locations`, { params: this.toParams(params) });
  }

  getDistinctDataTypes(params: Record<string, any>): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/senders/external/dataTypes`, { params: this.toParams(params) });
  }

  getDistinctTesterTypes(params: Record<string, any>): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/senders/external/testerTypes`, { params: this.toParams(params) });
  }

  getDistinctTestPhases(params: Record<string, any>): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/senders/external/testPhases`, { params: this.toParams(params) });
  }

  lookupSenders(params: Record<string, any>): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/senders/lookup`, { params: this.toParams(params) });
  }

  enqueue(senderId: number, req: EnqueueRequest): Observable<EnqueueResponse> {
    const headers = this.auth.getAuthHeaders();
    return this.http.post<EnqueueResponse>(`${this.base}/senders/${senderId}/enqueue`, req, { headers: headers || undefined });
  }

  getQueue(senderId: number, status: string, limit: number): Observable<any[]> {
    let params = new HttpParams().set('status', status).set('limit', String(limit));
    return this.http.get<any[]>(`${this.base}/senders/${senderId}/queue`, { params });
  }

  private toParams(input: Record<string, any>): HttpParams {
    let params = new HttpParams();
    Object.entries(input || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return params;
  }
}
