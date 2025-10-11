import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackendService, ExternalEnvironment, ExternalInstance, ExternalLocationSummary } from '../api/backend.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';


@Component({
  selector: 'app-stepper',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCardModule, MatProgressSpinnerModule, MatListModule, MatSnackBarModule, MatChipsModule, MatStepperModule, MatIconModule, MatDatepickerModule, MatNativeDateModule],
  templateUrl: './stepper.component.html',
  styleUrls: ['./stepper.component.css']
})
export class StepperComponent implements OnInit, OnDestroy {
  stepIndex = 0;

  sites: string[] = [];
  selectedSite = '';

  environments: ExternalEnvironment[] = [];
  selectedEnvironment = 'qa';

  instances: ExternalInstance[] = [];
  selectedInstanceKey: string | null = null;

  locations: ExternalLocationSummary[] = [];
  selectedLocationId: number | null = null;

  metadataLocation = '';
  testerType = '';
  dataType = '';
  testPhase = '';
  startDate: Date | null = null;
  endDate: Date | null = null;

  loading = false;
  loadingFilters = false;

  discovered: Array<any> = [];
  selectedDiscovered: Array<any> = [];
  senders: Array<{ idSender: number | null; name: string }> = [];
  selectedSenderId: number | null = null;

  monitoringQueue: Array<any> = [];
  enqueueResponse: { enqueued?: number | null; skipped?: string[] } | null = null;

  private monitoringTimer: any = null;

  constructor(private api: BackendService, private snack: MatSnackBar, private clipboard: Clipboard) {}

  ngOnInit(): void {
    this.loadSites();
    this.loadEnvironments();
  }

  ngOnDestroy(): void {
    this.stopMonitoringPoll();
  }

  private loadSites() {
    this.api.listSites().subscribe({
      next: (sites: string[]) => {
        this.sites = sites || [];
        if (!this.selectedSite && this.sites.length) {
          this.selectedSite = this.sites[0];
        }
      },
      error: (err: unknown) => console.error('Failed to load sites', err)
    });
  }

  private loadEnvironments() {
    this.api.listEnvironments().subscribe({
      next: (envs: ExternalEnvironment[]) => {
        this.environments = envs || [];
        if (!this.environments.length) {
          this.instances = [];
          this.locations = [];
          return;
        }
        const found = this.environments.some(e => (e.name || '').toLowerCase() === this.selectedEnvironment.toLowerCase());
        if (!found) {
          const qa = this.environments.find(e => (e.name || '').toLowerCase() === 'qa');
          this.selectedEnvironment = qa?.name || this.environments[0].name || this.selectedEnvironment;
        }
        this.loadInstances();
        this.loadLocations();
      },
      error: (err: unknown) => console.error('Failed to load environments', err)
    });
  }

  onEnvironmentChange() {
    this.selectedInstanceKey = null;
    this.selectedLocationId = null;
    this.metadataLocation = '';
    this.loadInstances();
    this.loadLocations();
  }

  private loadInstances() {
    if (!this.selectedEnvironment) {
      this.instances = [];
      this.selectedInstanceKey = null;
      return;
    }
    this.api.listInstances(this.selectedEnvironment).subscribe({
      next: (list: ExternalInstance[]) => {
        this.instances = list || [];
        if (this.selectedInstanceKey && !this.instances.some(i => i.key === this.selectedInstanceKey)) {
          this.selectedInstanceKey = null;
        }
      },
      error: (err: unknown) => {
        console.error('Failed to load instances', err);
        this.instances = [];
        this.selectedInstanceKey = null;
      }
    });
  }

  private loadLocations() {
    if (!this.selectedEnvironment) {
      this.locations = [];
      this.selectedLocationId = null;
      return;
    }
    this.loadingFilters = true;
    this.api.listLocations(this.selectedEnvironment).subscribe({
      next: (list: ExternalLocationSummary[]) => {
        this.locations = list || [];
        if (this.selectedLocationId && !this.locations.some(l => l.id === this.selectedLocationId)) {
          this.selectedLocationId = null;
        }
      },
      error: (err: unknown) => {
        console.error('Failed to load locations', err);
        this.locations = [];
        this.selectedLocationId = null;
      },
      complete: () => { this.loadingFilters = false; }
    });
  }

  canSearch(): boolean {
    const hasConnection = !!this.selectedLocationId || !!this.selectedInstanceKey;
    const locationText = this.metadataLocation || this.selectedLocationLabel();
    return hasConnection && !!locationText;
  }

  private selectedLocationLabel(): string {
    if (!this.selectedLocationId) return '';
    const match = this.locations.find(l => l.id === this.selectedLocationId);
    return match?.label || '';
  }

  doSearch() {
    if (!this.canSearch()) {
      this.snack.open('Provide a saved location or connection key and metadata location.', 'Close', { duration: 3000 });
      return;
    }

    const metadataFilter = this.metadataLocation || this.selectedLocationLabel();
    const params: Record<string, any> = {
      site: this.selectedSite || 'default',
      environment: this.selectedEnvironment || 'qa',
      metadataLocation: metadataFilter,
      dataType: this.dataType || undefined,
      testerType: this.testerType || undefined,
      testPhase: this.testPhase || undefined
    };

    if (this.selectedLocationId) {
      params.locationId = this.selectedLocationId;
    } else if (this.selectedInstanceKey) {
      params.connectionKey = this.selectedInstanceKey;
    }

    if (this.startDate) params.startDate = formatDate(this.startDate, 'yyyy-MM-dd', 'en-US');
    if (this.endDate) params.endDate = formatDate(this.endDate, 'yyyy-MM-dd', 'en-US');

    this.loading = true;
    this.api.lookupSenders(params).subscribe({
      next: (data: any[]) => {
        this.discovered = data || [];
        this.selectedDiscovered = [...this.discovered];
        const seen = new Set<number>();
        this.senders = [];
        this.discovered.forEach(d => {
          const id = d.idSender || null;
          if (id != null && !seen.has(id)) {
            seen.add(id);
            this.senders.push({ idSender: id, name: d.name });
          }
        });
        if (this.senders.length) {
          this.selectedSenderId = this.senders[0].idSender;
        }
        this.stepIndex = 1;
      },
      error: (err: unknown) => {
        console.error('Search failed', err);
        this.snack.open('Lookup failed', 'Close', { duration: 4000 });
      },
      complete: () => { this.loading = false; }
    });
  }

  goToStep(index: number) {
    if (index < 0) index = 0;
    if (index > 2) index = 2;
    if (index < this.stepIndex) {
      // When returning to earlier steps stop monitoring
      if (index < 2) {
        this.stopMonitoringPoll();
      }
    }
    this.stepIndex = index;
  }

  enqueueSelected() {
    const payloadIds = (this.selectedDiscovered || []).map(item => {
      const d = item?.value ?? item;
      return String(d.payloadId || d.idSender || d.id || d.metadataId || '').trim();
    }).filter(id => !!id);

    if (!payloadIds.length) {
      this.snack.open('Select at least one discovered entry to resend.', 'Close', { duration: 3000 });
      return;
    }

    const sid = this.selectedSenderId || (this.discovered.length ? this.discovered[0].idSender : null);
    if (!sid) {
      this.snack.open('A sender must be selected to enqueue items.', 'Close', { duration: 3000 });
      return;
    }

    const request = {
      senderId: sid,
      payloadIds,
      source: 'ui_stepper'
    };

    this.api.enqueue(sid, request).subscribe({
      next: (resp: unknown) => {
        const response = (resp ?? {}) as Record<string, unknown>;
        const enqueuedRaw = response['enqueuedCount'] ?? response['enqueued'];
        const enqueued = typeof enqueuedRaw === 'number' ? enqueuedRaw : null;
        const skippedRaw = response['skippedPayloads'] ?? response['skipped'];
        const skipped = Array.isArray(skippedRaw)
          ? skippedRaw.map(item => String(item))
          : [];
        this.enqueueResponse = { enqueued, skipped };
        this.snack.open(`Enqueued ${enqueued ?? payloadIds.length} payloads`, 'OK', { duration: 3500 });
        this.stepIndex = 2;
        this.startMonitoringPoll(sid);
      },
      error: (err: unknown) => {
        console.error('Enqueue failed', err);
        this.snack.open('Enqueue failed', 'Close', { duration: 4000 });
        this.stepIndex = 2;
        this.startMonitoringPoll(sid);
      }
    });
  }

  private startMonitoringPoll(senderId: number, intervalMs: number = 4000) {
    this.stopMonitoringPoll();
    this.loadMonitoring(senderId);
    this.monitoringTimer = setInterval(() => this.loadMonitoring(senderId), intervalMs);
  }

  private loadMonitoring(senderId: number) {
    if (!senderId) {
      this.monitoringQueue = [];
      return;
    }
    this.api.getQueue(senderId, 'NEW', 200).subscribe({
      next: (queue: any[] | null | undefined) => {
        this.monitoringQueue = queue || [];
      },
      error: (err: unknown) => {
        console.error('Monitoring load failed', err);
        this.monitoringQueue = [];
      }
    });
  }

  stopMonitoringPoll() {
    if (this.monitoringTimer) {
      clearInterval(this.monitoringTimer);
      this.monitoringTimer = null;
    }
  }

  copySkippedToClipboard() {
    if (!this.enqueueResponse?.skipped?.length) {
      this.snack.open('No skipped IDs to copy', 'OK', { duration: 2000 });
      return;
    }
    const text = this.enqueueResponse.skipped.join(',');
    this.clipboard.copy(text);
    this.snack.open('Skipped IDs copied to clipboard', 'OK', { duration: 2500 });
  }

  getStatusColor(status: string | null): 'primary' | 'accent' | 'warn' | undefined {
    if (!status) return undefined;
    const normalized = status.toUpperCase();
    if (normalized === 'FAILED' || normalized === 'FAIL') return 'warn';
    if (['STAGED', 'PUSHED', 'PROCESSING'].includes(normalized)) return 'accent';
    return 'primary';
  }

  reset() {
    this.stopMonitoringPoll();
    this.stepIndex = 0;
    this.discovered = [];
    this.selectedDiscovered = [];
    this.senders = [];
    this.selectedSenderId = null;
    this.enqueueResponse = null;
    this.monitoringQueue = [];
  }
}
