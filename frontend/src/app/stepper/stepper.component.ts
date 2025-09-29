import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackendService } from '../api/backend.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { MatSnackBarModule } from '@angular/material/snack-bar';


@Component({
  selector: 'app-stepper',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCardModule, MatProgressSpinnerModule, MatListModule, MatSnackBarModule, MatExpansionModule, MatChipsModule],
  templateUrl: './stepper.component.html',
  styleUrls: ['./stepper.component.css']
})
export class StepperComponent {
  // stepper state: 1=inputs, 2=list, 3=monitor
  step = 1;

  // inputs
  site = '';
  environment = 'qa';
  connectionKey = '';
  locationId: number | null = null;
  metadataLocation = '';

  // results
  discovered: Array<any> = [];
  // available senders (from discovered results)
  senders: Array<{idSender:number|null,name:string}> = [];
  selectedSenderId: number | null = null;
  loading = false;

  // monitoring
  monitoringQueue: Array<any> = [];
  constructor(private api: BackendService, private snack: MatSnackBar, private clipboard: Clipboard) {}
  enqueueResponse: { enqueued?: number; skipped?: string[] } | null = null;
  private monitoringTimer: any = null;
  showFullResponse = false;

 

  canSearch(): boolean {
    // require either connectionKey or locationId and metadataLocation
    return (!!this.connectionKey || !!this.locationId) && !!this.metadataLocation;
  }

  doSearch() {
    if (!this.canSearch()) return;
    this.loading = true;
    // reuse lookupSenders via BackendService: caller expects a senderId, but we'll call lookup with metadataLocation
    this.api.lookupSenders({ connectionKey: this.connectionKey || undefined, locationId: this.locationId || undefined, metadataLocation: this.metadataLocation, environment: this.environment }).subscribe(
      (data: any[]) => {
        this.discovered = data || [];
        // build senders list from discovered rows (unique idSender)
        const seen = new Set<number>();
        this.senders = [];
        (this.discovered || []).forEach(d => {
          const id = d.idSender || null;
          if (id != null && !seen.has(id)) { seen.add(id); this.senders.push({ idSender: id, name: d.name }); }
        });
        this.selectedSenderId = this.senders.length ? this.senders[0].idSender : null;
        this.loading = false;
        this.step = 2;
      },
      (err: any) => { console.error('Search failed', err); this.loading = false; }
    );
  }

  submitSelection() {
    // collect selected payload ids (for demo we use discovered ids if present)
    // kept for compatibility with old callers
    this.submitSelectionFromList((this.discovered || []).map(d => ({ value: d } as any)) as any);
  }

  submitSelectionFromList(selected: any[]) {
    const payloadIds = (selected || []).map(s => {
      const d = s.value || s;
      return String(d.idSender || d.name || d.id || d.payloadId || '');
    }).filter((x: string) => !!x);
    if (!payloadIds.length) { this.step = 3; this.loadMonitoring(); return; }
    const req = { senderId: this.selectedSenderId, payloadIds: payloadIds, source: 'ui_stepper' } as any;
    const sid = this.selectedSenderId || 0;
    this.api.enqueue(sid, req).subscribe((resp: any) => {
      // resp expected to be { enqueued: number, skipped: [ ... ] } (see backend dto)
      const enq = resp && resp.enqueuedCount != null ? resp.enqueuedCount : (resp && resp.enqueued ? resp.enqueued : null);
      const skipped = resp && resp.skippedPayloads ? resp.skippedPayloads : (resp && resp.skipped ? resp.skipped : []);
      const enqNum = enq != null ? enq : (resp && resp.enqueued ? resp.enqueued : null);
      const skippedArr = skipped || [];
      this.enqueueResponse = { enqueued: enqNum, skipped: skippedArr };
      const msg = `Enqueued ${enqNum != null ? enqNum : '?'}; skipped ${skippedArr.length}`;
      this.snack.open(msg, 'OK', { duration: 4000 });
      this.step = 3;
      this.startMonitoringPoll();
    }, (err: any) => { console.error('Enqueue failed', err); this.step = 3; this.loadMonitoring(); this.snack.open('Enqueue failed', 'Close', { duration: 5000 }); });
  }

  selectAll(sel: any) {
    if (!sel) return;
    sel.selectAll();
  }

  clearSelection(sel: any) {
    if (!sel) return;
    sel.deselectAll();
  }

  copySkippedToClipboard() {
    if (!this.enqueueResponse || !this.enqueueResponse.skipped || !this.enqueueResponse.skipped.length) {
      this.snack.open('No skipped IDs to copy', 'OK', { duration: 2000 });
      return;
    }
    const text = this.enqueueResponse.skipped.join(',');
    this.clipboard.copy(text);
    this.snack.open('Skipped IDs copied to clipboard', 'OK', { duration: 2500 });
  }

  getStatusColor(status: string | null): 'primary' | 'accent' | 'warn' | undefined {
    if (!status) return undefined;
    const s = status.toUpperCase();
    if (s === 'FAILED' || s === 'FAIL') return 'warn';
    if (s === 'STAGED' || s === 'PUSHED' || s === 'PROCESSING') return 'accent';
    return 'primary';
  }

  loadMonitoring() {
    // For demonstration, call the queue endpoint for a selected sender (if any)
    // We'll attempt to use a sender id from discovered list if present
    const sid = this.discovered && this.discovered.length && this.discovered[0].idSender ? this.discovered[0].idSender : 0;
    this.api.getQueue(sid, 'NEW', 200).subscribe((q: any[]) => { this.monitoringQueue = q || []; }, (err: any) => { console.error('Monitoring load failed', err); this.monitoringQueue = []; });
  }

  startMonitoringPoll(intervalMs: number = 3000) {
    this.stopMonitoringPoll();
    this.loadMonitoring();
    this.monitoringTimer = setInterval(() => {
      this.loadMonitoring();
    }, intervalMs);
  }

  stopMonitoringPoll() {
    if (this.monitoringTimer) { clearInterval(this.monitoringTimer); this.monitoringTimer = null; }
  }

  // ensure timer cleanup when user navigates back
  backToInputs() { this.stopMonitoringPoll(); this.step = 1; }

  
}
