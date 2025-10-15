import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StepperSelectionEvent } from '@angular/cdk/stepper';
import {
  BackendService,
  DiscoveryPreviewRequest,
  DiscoveryPreviewResponse,
  DiscoveryPreviewRow,
  ReloadFilterOptions,
  SenderOption,
  StagePayloadRequestBody,
  StagePayloadResponseBody,
  StageRecordPage,
  StageRecordView,
  StageStatus
} from '../api/backend.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatListModule } from '@angular/material/list';


@Component({
  selector: 'app-stepper',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCardModule, MatProgressSpinnerModule, MatListModule, MatSnackBarModule, MatStepperModule, MatIconModule, MatDatepickerModule, MatNativeDateModule, MatCheckboxModule],
  templateUrl: './stepper.component.html',
  styleUrls: ['./stepper.component.css']
})
export class StepperComponent implements OnInit, OnDestroy {
  stepIndex = 0;

  sites: string[] = [];
  selectedSite: string | null = null;
  selectedEnvironment: string | null = 'qa';

  filterOptions: ReloadFilterOptions | null = null;
  filtersLoading = false;
  sendersLoading = false;
  testPhaseLoading = false;

  selectedLocation = '';
  selectedDataType = '';
  selectedTesterType = '';
  selectedTestPhase = '';
  startDate: Date | null = null;
  endDate: Date | null = null;

  loading = false;

  senderOptions: SenderOption[] = [];
  selectedSenderId: number | null = null;
  testPhaseOptions: string[] = [];

  previewLoading = false;
  previewRows: DiscoveryPreviewRow[] = [];
  previewTotal = 0;
  previewPage = 0;
  previewSize = 25;
  previewSizes = [25, 50, 100];
  previewRowCache = new Map<string, DiscoveryPreviewRow>();
  previewSelectedKeys = new Set<string>();
  selectAllPage = true;
  previewLoaded = false;
  selectedCount = 0;

  triggerDispatch = false;
  staging = false;
  stageResponse: StagePayloadResponseBody | null = null;

  statusLoading = false;
  stageStatus: StageStatus[] = [];

  stageRecordsLoading = false;
  stageRecords: StageRecordView[] = [];
  stageRecordsTotal = 0;
  stageRecordsPage = 0;
  stageRecordsSize = 25;
  stageRecordsSizes = [25, 50, 100];
  stageRecordsStatus: string = 'ALL';

  constructor(private api: BackendService, private snack: MatSnackBar, private clipboard: Clipboard) {}

  ngOnInit(): void {
    this.loadSites();
  }

  ngOnDestroy(): void {
    // no recurring timers currently
  }

  private loadSites() {
    this.api.listSites().subscribe({
      next: (sites: string[]) => {
        this.sites = sites || [];
        if (this.selectedSite && !this.sites.includes(this.selectedSite)) {
          this.selectedSite = null;
          this.onSiteChange();
        }
      },
      error: (err: unknown) => console.error('Failed to load sites', err)
    });
  }

  onSiteChange() {
    this.filterOptions = null;
    this.selectedLocation = '';
    this.selectedDataType = '';
    this.selectedTesterType = '';
    this.selectedTestPhase = '';
    this.testPhaseOptions = [];
    this.resetPreview();
    this.senderOptions = [];
    this.selectedSenderId = null;
    this.stepIndex = 0;
    this.filtersLoading = false;
    this.sendersLoading = false;
    this.testPhaseLoading = false;
    if (!this.selectedSite) {
      return;
    }
    this.loadFiltersForSite(this.selectedSite);
    this.loadSendersForSite(this.selectedSite);
  }

  private loadFiltersForSite(site: string) {
    this.filtersLoading = true;
    this.api.getReloadFilters(site).subscribe({
      next: (options: ReloadFilterOptions) => {
        const normalized: ReloadFilterOptions = {
          locations: options?.locations?.filter(Boolean) ?? [],
          dataTypes: options?.dataTypes?.filter(Boolean) ?? [],
          testerTypes: options?.testerTypes?.filter(Boolean) ?? [],
          dataTypeExt: options?.dataTypeExt?.filter((v: string | null | undefined) => v !== undefined) ?? []
        };
        this.filterOptions = normalized;
        this.filtersLoading = false;
      },
      error: (err: unknown) => {
        console.error('Failed to load reload filters', err);
        this.filterOptions = { locations: [], dataTypes: [], testerTypes: [], dataTypeExt: [] };
        this.filtersLoading = false;
      }
    });
  }

  private loadSendersForSite(site: string) {
    this.sendersLoading = true;
    const previousSelection = this.selectedSenderId;
    this.senderOptions = [];
    this.selectedSenderId = null;
    this.api.getExternalSenders({ connectionKey: site }).subscribe({
      next: (senders: SenderOption[]) => {
        this.senderOptions = (senders || []).filter((s): s is SenderOption => !!s && s.idSender != null);
        if (previousSelection != null && this.senderOptions.some(opt => opt.idSender === previousSelection)) {
          this.selectedSenderId = previousSelection;
        } else {
          this.selectedSenderId = null;
        }
        this.refreshTestPhasesIfReady();
      },
      error: (err: unknown) => {
        console.error('Failed to load senders', err);
        this.senderOptions = [];
        this.selectedSenderId = null;
        this.sendersLoading = false;
      },
      complete: () => {
        this.sendersLoading = false;
      }
    });
  }

  private getSelectedSenderName(): string | null {
    if (this.selectedSenderId == null) {
      return null;
    }
    const selected = this.senderOptions.find(opt => opt.idSender === this.selectedSenderId);
    return selected?.name ?? null;
  }

  onLocationChanged() {
    this.clearDiscoveryState();
    this.refreshTestPhasesIfReady();
  }

  onDataTypeChanged() {
    this.clearDiscoveryState();
    this.refreshTestPhasesIfReady();
  }

  onTesterTypeChanged() {
    this.clearDiscoveryState();
    this.refreshTestPhasesIfReady();
  }

  onSenderChanged() {
    this.clearDiscoveryState();
    this.refreshTestPhasesIfReady();
  }

  private clearDiscoveryState() {
    this.resetPreview();
    this.stageResponse = null;
    this.testPhaseOptions = [];
    this.selectedTestPhase = '';
    this.testPhaseLoading = false;
    this.loading = false;
    this.stepIndex = 0;
  }

  private refreshTestPhasesIfReady() {
    if (!this.selectedSite) {
      this.testPhaseOptions = [];
      this.selectedTestPhase = '';
      return;
    }

    const hasRequired = !!(this.selectedLocation && this.selectedDataType && this.selectedTesterType && this.selectedSenderId != null);
    if (!hasRequired) {
      this.testPhaseOptions = [];
      this.selectedTestPhase = '';
      return;
    }

    this.testPhaseLoading = true;
    const params: Record<string, any> = {
      connectionKey: this.selectedSite,
      location: this.selectedLocation,
      dataType: this.selectedDataType,
      testerType: this.selectedTesterType,
      senderId: this.selectedSenderId ?? undefined,
      senderName: this.getSelectedSenderName() || undefined
    };

    this.api.getDistinctTestPhases(params).subscribe({
      next: (phases: string[] | null | undefined) => {
        let hasBlank = false;
        const normalized = (phases || []).map(phase => {
          const value = (phase ?? '').trim();
          if (!value.length) {
            hasBlank = true;
          }
          return value;
        }).filter(phase => phase.length > 0);
        const unique = Array.from(new Set(normalized));
        if (hasBlank) {
          this.testPhaseOptions = [''].concat(unique);
        } else {
          this.testPhaseOptions = unique;
        }
        if (!this.testPhaseOptions.length) {
          this.selectedTestPhase = '';
        } else if (this.selectedTestPhase && !this.testPhaseOptions.includes(this.selectedTestPhase)) {
          this.selectedTestPhase = '';
        }
      },
      error: (err: unknown) => {
        console.error('Failed to load test phases', err);
        this.testPhaseOptions = [];
        this.selectedTestPhase = '';
      },
      complete: () => {
        this.testPhaseLoading = false;
      }
    });
  }

  canSearch(): boolean {
    return !!(this.selectedSite && this.selectedLocation && this.selectedDataType && this.selectedTesterType && this.selectedSenderId != null);
  }

  doPreview(page: number = 0) {
    if (!this.canSearch()) {
      this.snack.open('Select all required filters before searching.', 'Close', { duration: 3000 });
      return;
    }

    const site = this.selectedSite as string;
    const request: DiscoveryPreviewRequest = {
      site,
      environment: this.selectedEnvironment || undefined,
      startDate: this.formatStartDate(),
      endDate: this.formatEndDate(),
      testerType: this.selectedTesterType || undefined,
      dataType: this.selectedDataType || undefined,
      testPhase: this.selectedTestPhase || undefined,
      location: this.selectedLocation || undefined,
      page,
      size: this.previewSize
    };

    this.loading = true;
    this.previewLoading = true;
    const senderId = this.selectedSenderId as number;
    this.api.previewDiscovery(senderId, request).subscribe({
      next: (response: DiscoveryPreviewResponse) => {
        this.previewLoaded = true;
  this.previewRows = response?.items ?? [];
  this.previewTotal = response?.total ?? 0;
  this.previewPage = response?.page ?? page;
  this.previewSize = response?.size ?? this.previewSize;
  this.cachePreviewRows(this.previewRows);
        this.updateSelectAllState();
        this.recalculateSelectedCount();
        this.stepIndex = Math.max(this.stepIndex, 1);
        if (this.previewRows.length === 0 && this.previewTotal > 0 && this.previewPage > 0) {
          // If page is beyond total (race condition), reset to first page
          this.doPreview(0);
        }
      },
      error: (err: unknown) => {
        console.error('Preview failed', err);
        this.snack.open('Discovery preview failed', 'Close', { duration: 4000 });
        this.loading = false;
        this.previewLoading = false;
      },
      complete: () => {
        this.loading = false;
        this.previewLoading = false;
      }
    });
  }

  goToStep(index: number) {
    if (index < 0) index = 0;
    if (index > 2) index = 2;
    this.stepIndex = index;
    if (index === 2) {
      this.refreshMonitoring();
    }
  }

  onStepperChange(event: StepperSelectionEvent) {
    this.stepIndex = event.selectedIndex;
    if (this.stepIndex === 2) {
      this.refreshMonitoring();
    }
  }

  changePreviewPage(delta: number) {
    const target = this.previewPage + delta;
    if (target < 0) return;
    const lastPage = this.previewLastPage;
    if (lastPage >= 0 && target > lastPage) return;
    this.doPreview(target);
  }

  changePreviewSize(size: number) {
    if (!size || size <= 0) return;
    this.previewSize = size;
    this.previewPage = 0;
    this.doPreview(0);
  }

  toggleRow(row: DiscoveryPreviewRow, checked: boolean) {
    const key = this.rowKey(row);
    if (!key) return;
    if (checked) {
      this.previewSelectedKeys.add(key);
    } else {
      this.previewSelectedKeys.delete(key);
    }
    this.updateSelectAllState();
    this.recalculateSelectedCount();
  }

  toggleSelectAllCurrentPage(checked: boolean) {
    for (const row of this.previewRows) {
      const key = this.rowKey(row);
      if (!key) continue;
      if (checked) {
        this.previewSelectedKeys.add(key);
      } else {
        this.previewSelectedKeys.delete(key);
      }
    }
    this.selectAllPage = checked;
    this.updateSelectAllState();
    this.recalculateSelectedCount();
  }

  isRowSelected(row: DiscoveryPreviewRow): boolean {
    const key = this.rowKey(row);
    return key ? this.previewSelectedKeys.has(key) : false;
  }

  stageSelected() {
    if (!this.selectedSite || !this.selectedSenderId) {
      this.snack.open('Site and sender must be selected.', 'Close', { duration: 3000 });
      return;
    }
    const payloads = this.collectSelectedPayloads();
    if (!payloads.length) {
      this.snack.open('Select at least one payload to stage.', 'Close', { duration: 3000 });
      return;
    }

    const body: StagePayloadRequestBody = {
      site: this.selectedSite,
      environment: this.selectedEnvironment || undefined,
      senderId: this.selectedSenderId,
      payloads,
      triggerDispatch: this.triggerDispatch
    };

    this.staging = true;
    this.api.stagePayloads(this.selectedSenderId, body).subscribe({
      next: (response: StagePayloadResponseBody) => {
        this.stageResponse = response;
        this.snack.open(`Staged ${response?.staged ?? payloads.length} payloads`, 'OK', { duration: 3500 });
        this.stepIndex = 2;
        this.refreshMonitoring();
      },
      error: (err: unknown) => {
        console.error('Staging failed', err);
        this.snack.open('Failed staging payloads', 'Close', { duration: 4000 });
        this.staging = false;
      },
      complete: () => {
        this.staging = false;
      }
    });
  }

  refreshMonitoring() {
    if (!this.selectedSite || !this.selectedSenderId) {
      return;
    }
    this.loadStageStatus();
    this.loadStageRecords(this.stageRecordsPage);
  }

  changeStageRecordsPage(delta: number) {
    const target = this.stageRecordsPage + delta;
    if (target < 0) return;
    const last = this.stageRecordsLastPage;
    if (last >= 0 && target > last) return;
    this.loadStageRecords(target);
  }

  changeStageRecordsSize(size: number) {
    if (!size || size <= 0) return;
    this.stageRecordsSize = size;
    this.stageRecordsPage = 0;
    this.loadStageRecords(0);
  }

  reset() {
    this.stepIndex = 0;
    this.resetPreview();
    this.stageResponse = null;
    this.stageStatus = [];
    this.statusLoading = false;
    this.stageRecords = [];
    this.stageRecordsTotal = 0;
    this.stageRecordsPage = 0;
    this.stageRecordsStatus = 'ALL';
    this.stageRecordsLoading = false;
    this.selectedEnvironment = 'qa';
    this.triggerDispatch = false;
  }

  copyDuplicatesToClipboard() {
    if (!this.stageResponse?.duplicatePayloads?.length) {
      this.snack.open('No duplicate payloads to copy.', 'OK', { duration: 2500 });
      return;
    }
    const text = this.stageResponse.duplicatePayloads.join(',');
    this.clipboard.copy(text);
    this.snack.open('Duplicate payload IDs copied.', 'OK', { duration: 2500 });
  }

  private resetPreview() {
    this.previewRows = [];
    this.previewRowCache.clear();
    this.previewSelectedKeys.clear();
    this.previewTotal = 0;
    this.previewPage = 0;
    this.previewLoaded = false;
    this.selectedCount = 0;
    this.selectAllPage = true;
    this.previewLoading = false;
  }

  private cachePreviewRows(rows: DiscoveryPreviewRow[]) {
    for (const row of rows) {
      const key = this.rowKey(row);
      if (!key) continue;
      const existed = this.previewRowCache.has(key);
      this.previewRowCache.set(key, row);
      if (!existed && !this.previewSelectedKeys.has(key)) {
        this.previewSelectedKeys.add(key);
      }
    }
  }

  private updateSelectAllState() {
    if (!this.previewRows.length) {
      this.selectAllPage = false;
      return;
    }
    const selectedOnPage = this.previewRows.filter(row => this.isRowSelected(row)).length;
    this.selectAllPage = selectedOnPage === this.previewRows.length;
  }

  private recalculateSelectedCount() {
    this.selectedCount = this.previewSelectedKeys.size;
  }

  private collectSelectedPayloads(): Array<{ metadataId: string; dataId: string }> {
    const results: Array<{ metadataId: string; dataId: string }> = [];
    for (const key of this.previewSelectedKeys) {
      const row = this.previewRowCache.get(key);
      if (!row) continue;
      if (!row.metadataId || !row.dataId) continue;
      results.push({ metadataId: row.metadataId, dataId: row.dataId });
    }
    return results;
  }

  private loadStageStatus() {
    if (!this.selectedSite) {
      this.stageStatus = [];
      return;
    }
    this.statusLoading = true;
    this.api.getStageStatusFor(this.selectedSite, this.selectedSenderId ?? undefined).subscribe({
      next: (status: StageStatus[]) => {
        this.stageStatus = status || [];
      },
      error: (err: unknown) => {
        console.error('Failed loading stage status', err);
        this.stageStatus = [];
        this.statusLoading = false;
      },
      complete: () => {
        this.statusLoading = false;
      }
    });
  }

  loadStageRecords(page: number) {
    if (!this.selectedSite || !this.selectedSenderId) {
      this.stageRecords = [];
      this.stageRecordsTotal = 0;
      return;
    }
    this.stageRecordsLoading = true;
    const statusFilter = this.stageRecordsStatus === 'ALL' ? '' : this.stageRecordsStatus;
    this.api.listStageRecords(this.selectedSite, this.selectedSenderId, statusFilter, page, this.stageRecordsSize).subscribe({
      next: (response: StageRecordPage) => {
        this.stageRecords = response?.items ?? [];
        this.stageRecordsTotal = response?.total ?? 0;
        this.stageRecordsPage = response?.page ?? page;
      },
      error: (err: unknown) => {
        console.error('Failed loading stage records', err);
        this.stageRecords = [];
        this.stageRecordsTotal = 0;
        this.stageRecordsLoading = false;
      },
      complete: () => {
        this.stageRecordsLoading = false;
      }
    });
  }

  private rowKey(row: DiscoveryPreviewRow | null | undefined): string | null {
    if (!row) return null;
    const metadata = (row.metadataId ?? '').trim();
    const data = (row.dataId ?? '').trim();
    if (!metadata || !data) {
      return null;
    }
    return `${metadata}|${data}`;
  }

  get previewLastPage(): number {
    if (this.previewTotal <= 0 || this.previewSize <= 0) return -1;
    return Math.max(Math.ceil(this.previewTotal / this.previewSize) - 1, 0);
  }

  get stageRecordsLastPage(): number {
    if (this.stageRecordsTotal <= 0 || this.stageRecordsSize <= 0) return -1;
    return Math.max(Math.ceil(this.stageRecordsTotal / this.stageRecordsSize) - 1, 0);
  }

  private formatStartDate(): string | null {
    if (!this.startDate) {
      return null;
    }
    return `${formatDate(this.startDate, 'yyyy-MM-dd', 'en-US')} 00:00:00`;
  }

  private formatEndDate(): string | null {
    if (!this.endDate) {
      return null;
    }
    return `${formatDate(this.endDate, 'yyyy-MM-dd', 'en-US')} 23:59:59`;
  }
}
