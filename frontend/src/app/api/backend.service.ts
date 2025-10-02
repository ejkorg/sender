import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExternalEnvironment, ExternalLocation, SenderCandidate, EnqueueRequest } from './models';
import { AuthService } from '../auth/auth.service';

@Injectable({ providedIn: 'root' })
export class BackendService {
  private base = '/api';

  constructor(private http: HttpClient, private auth: AuthService) {}

  listEnvironments(): Observable<ExternalEnvironment[]> {
    return this.http.get<ExternalEnvironment[]>(`${this.base}/environments`);
  }

  listLocations(envName: string): Observable<ExternalLocation[]> {
    return this.http.get<ExternalLocation[]>(`${this.base}/environments/${encodeURIComponent(envName)}/locations`);
  }

  listInstances(environment: string){
    const params = new HttpParams().set('environment', environment);
    return this.http.get<{key:string,label:string,environment:string}[]>(`${this.base}/external/instances`, { params });
  }

  lookupSenders(params: { locationId?: number; connectionKey?: string; metadataLocation?: string; dataType?: string; testerType?: string; testPhase?: string; environment?: string; } ):
    Observable<SenderCandidate[]> {
    let p = new HttpParams();
    Object.entries(params).forEach(([k, v]) => { if (v != null) p = p.set(k, String(v)); });
    return this.http.get<SenderCandidate[]>(`${this.base}/senders/lookup`, { params: p });
  }

  getDistinctTesterTypes(options: { locationId?: number; connectionKey?: string; location?: string; dataType?: string; testPhase?: string; environment?: string }){
    let p = new HttpParams();
    Object.entries(options).forEach(([k,v]) => { if (v != null) p = p.set(k, String(v)); });
    const headers = this.auth.getAuthHeaders();
    return this.http.get<string[]>(`${this.base}/senders/external/testerTypes`, { params: p, headers: headers || undefined });
  }

  getDistinctDataTypes(options: { locationId?: number; connectionKey?: string; location?: string; testerType?: string; testPhase?: string; environment?: string }){
    let p = new HttpParams();
    Object.entries(options).forEach(([k,v]) => { if (v != null) p = p.set(k, String(v)); });
    const headers = this.auth.getAuthHeaders();
    return this.http.get<string[]>(`${this.base}/senders/external/dataTypes`, { params: p, headers: headers || undefined });
  }

  getDistinctLocations(options: { locationId?: number; connectionKey?: string; dataType?: string; testerType?: string; testPhase?: string; environment?: string }){
    let p = new HttpParams();
    Object.entries(options).forEach(([k,v]) => { if (v != null) p = p.set(k, String(v)); });
    const headers = this.auth.getAuthHeaders();
    return this.http.get<string[]>(`${this.base}/senders/external/locations`, { params: p, headers: headers || undefined });
  }

  discover(senderId: number, opts: any){
    let p = new HttpParams();
    Object.entries(opts || {}).forEach(([k,v]) => { if (v != null) p = p.set(k, String(v)); });
    const headers = this.auth.getAuthHeaders();
    return this.http.post(`${this.base}/senders/${senderId}/discover`, null, { params: p, headers: headers || undefined, responseType: 'text' });
  }

  enqueue(senderId: number, req: EnqueueRequest){
    return this.http.post(`${this.base}/senders/${senderId}/enqueue`, req);
  }

  getQueue(senderId: number, status: string = 'NEW', limit: number = 100) {
    const params = new HttpParams().set('status', status).set('limit', String(limit));
    return this.http.get<any[]>(`${this.base}/senders/${senderId}/queue`, { params });
  }
}
