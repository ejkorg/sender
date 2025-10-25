import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export interface StageStatus {
  site: string;
  senderId: number;
  total: number;
  ready: number;
  enqueued: number;
  failed: number;
  completed: number;
  users: StageUserStatus[];
}

export interface StageUserStatus {
  username: string | null;
  total: number;
  ready: number;
  enqueued: number;
  failed: number;
  completed: number;
  lastRequestedAt: string | null;
}

export interface DiscoveryPreviewRequest {
  site: string;
  environment?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  // Support multiple lot/wafer pairs
  lots?: string[] | null;
  wafers?: string[] | null;
  testerType?: string | null;
  dataType?: string | null;
  testPhase?: string | null;
  location?: string | null;
  page: number;
  size: number;
}

export interface DiscoveryPreviewRow {
  metadataId: string | null;
  dataId: string | null;
  lot: string | null;
  wafer?: string | null;
  originalFileName?: string | null;
  endTime: string | null;
}

export interface DiscoveryPreviewResponse {
  items: DiscoveryPreviewRow[];
  total: number;
  page: number;
  size: number;
  debugSql?: string;
}

export interface PreviewDuplicateRequest {
  site: string;
  senderId?: number | null;
  items: Array<{ metadataId: string | null; dataId: string | null }>;
}

export interface StagePayloadRequestBody {
  site: string;
  environment?: string | null;
  senderId?: number | null;
  payloads: Array<{ metadataId: string; dataId: string }>;
  triggerDispatch: boolean;
  forceDuplicates?: boolean;
}

export interface StagePayloadResponseBody {
  staged: number;
  duplicates: number;
  duplicatePayloads: DuplicatePayloadInfo[];
  dispatched: number;
  requiresConfirmation?: boolean;
}

export interface DuplicatePayloadInfo {
  metadataId: string;
  dataId: string;
  previousStatus: string | null;
  processedAt: string | null;
  stagedBy: string | null;
  stagedAt: string | null;
  lastRequestedBy: string | null;
  lastRequestedAt: string | null;
  requiresConfirmation?: boolean;
  wafer?: string | null;
}

export interface StageRecordView {
  id: number;
  site: string;
  senderId: number;
  metadataId: string | null;
  dataId: string | null;
  status: string;
  errorMessage: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  processedAt?: string | null;
  stagedBy?: string | null;
  lastRequestedBy?: string | null;
  lastRequestedAt?: string | null;
  wafer?: string | null;
}

export interface StageRecordPage {
  items: StageRecordView[];
  total: number;
  page: number;
  size: number;
}

export interface ReloadRequest {
  site: string;
  senderId: string;
  startDate?: string;
  stagedBy: string | null;
  lastRequestedBy: string | null;
  lastRequestedAt: string | null;
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

export interface DispatchRequest {
  site: string;
  senderId?: number | null;
  limit?: number | null;
}

export interface DispatchResponse {
  site: string;
  senderId: number;
  dispatched: number;
}

export interface ReloadFilterOptions {
  locations: string[];
  dataTypes: string[];
  testerTypes: string[];
  dataTypeExt?: string[];
}

export interface SenderOption {
  idSender: number | null;
  name: string;
  id?: number | null;
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

  getStageStatusFor(site: string, senderId?: number | null): Observable<StageStatus[]> {
    let params = new HttpParams().set('site', site);
    if (senderId != null) {
      params = params.set('senderId', String(senderId));
    }
    return this.http.get<StageStatus[]>(`${this.base}/stage/status/by`, { params });
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

  getExternalSenders(params: Record<string, any>): Observable<SenderOption[]> {
    return this.http.get<SenderOption[]>(`${this.base}/senders/external/senders`, { params: this.toParams(params) });
  }

  lookupSenders(params: Record<string, any>): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/senders/lookup`, { params: this.toParams(params) });
  }

  previewDiscovery(senderId: number, req: DiscoveryPreviewRequest): Observable<DiscoveryPreviewResponse> {
    const headers = this.auth.getAuthHeaders();
    return this.http.post<DiscoveryPreviewResponse>(`${this.base}/senders/${senderId}/discover/preview`, req, { headers: headers || undefined });
  }

  previewDuplicates(senderId: number, req: PreviewDuplicateRequest): Observable<DuplicatePayloadInfo[]> {
    const headers = this.auth.getAuthHeaders();
    return this.http.post<DuplicatePayloadInfo[]>(`${this.base}/senders/${senderId}/preview/duplicates`, req, { headers: headers || undefined });
  }

  stagePayloads(senderId: number, body: StagePayloadRequestBody): Observable<StagePayloadResponseBody> {
    const headers = this.auth.getAuthHeaders();
    return this.http.post<StagePayloadResponseBody>(`${this.base}/senders/${senderId}/stage`, body, { headers: headers || undefined });
  }

  enqueue(senderId: number, req: EnqueueRequest): Observable<EnqueueResponse> {
    const headers = this.auth.getAuthHeaders();
    return this.http.post<EnqueueResponse>(`${this.base}/senders/${senderId}/enqueue`, req, { headers: headers || undefined });
  }

  dispatchSender(senderId: number, req: DispatchRequest): Observable<DispatchResponse> {
    const headers = this.auth.getAuthHeaders();
    return this.http.post<DispatchResponse>(`${this.base}/senders/${senderId}/dispatch`, req, { headers: headers || undefined });
  }

  getQueue(senderId: number, status: string, limit: number): Observable<any[]> {
    let params = new HttpParams().set('status', status).set('limit', String(limit));
    return this.http.get<any[]>(`${this.base}/senders/${senderId}/queue`, { params });
  }

  listStageRecords(site: string, senderId: number, status: string, page: number, size: number): Observable<StageRecordPage> {
    let params = new HttpParams().set('site', site).set('page', String(page)).set('size', String(size));
    if (senderId) {
      params = params.set('senderId', String(senderId));
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<StageRecordPage>(`${this.base}/stage/records`, { params });
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
