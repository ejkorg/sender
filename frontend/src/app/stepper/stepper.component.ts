import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
import { DuplicateWarningDialogComponent } from './duplicate-warning-dialog.component';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ModalService } from '../ui/modal.service';
import { ToastService } from '../ui/toast.service';
import { CsvExportService } from '../ui/csv-export.service';
import { TruncatePipe } from '../ui/truncate.pipe';


@Component({
  selector: 'app-stepper',
  standalone: true,
  // Note: DuplicateWarningDialogComponent is created dynamically via ModalService
  imports: [CommonModule, FormsModule, TruncatePipe],
  templateUrl: './stepper.component.html'
})
export class StepperComponent implements OnInit, OnDestroy {
  stepIndex = 0;

  sites: string[] = [];
  selectedSite: string | null = null;
  // support up to 5 lot/wafer pairs
  lotWaferPairs: Array<{ lot?: string | null; wafer?: string | null }> = [{ lot: null, wafer: null }];
  // configurable maximum number of lot/wafer pairs (default 5)
  readonly maxLotWaferPairs = 5;
  isAdmin = false;
  // duplicate display mode: 'group' shows grouped duplicates below records; 'interleave' merges duplicates into the record list chronologically
  duplicateDisplayMode: 'group' | 'interleave' = 'group';

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

  // track whether sender was auto-resolved
  senderAutoResolved = false;

  loading = false;

  senderOptions: SenderOption[] = [];
  selectedSenderId: number | null = null;
  testPhaseOptions: string[] = [];

  previewLoading = false;
  previewRows: DiscoveryPreviewRow[] = [];
  // Map of preview duplicate lookups (key: "metadata|data") -> DuplicatePayloadInfo
  previewDuplicatesMap = new Map<string, import('../api/backend.service').DuplicatePayloadInfo>();
  previewTotal = 0;
  previewPage = 0;
  previewSize = 25;
  previewSizes = [25, 50, 100];
  previewRowCache = new Map<string, DiscoveryPreviewRow>();
  previewSelectedKeys = new Set<string>();
  selectAllPage = true;
  previewLoaded = false;
  selectedCount = 0;
  previewDebugSql: string | null = null;

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
  currentUser: string | null = null;
  private userSub?: Subscription;
  readonly uiDateFormat = 'yyyy-MM-dd HH:mm:ss';
  // duplicate prompt handled by ModalService

  constructor(private api: BackendService,
              private toast: ToastService,
              private auth: AuthService,
              private modal: ModalService,
              private csv: CsvExportService) {}

  ngOnInit(): void {
    this.loadSites();
    this.userSub = this.auth.user$.subscribe(user => {
      this.currentUser = user?.username ?? null;
      this.isAdmin = !!(user && Array.isArray(user.roles) && user.roles.includes('ROLE_ADMIN'));
      // environment filter removed from stepper UI/logic
    });
    // load persisted duplicate display preference if present
    try {
      const saved = localStorage.getItem('duplicateDisplayMode');
      if (saved === 'group' || saved === 'interleave') {
        this.duplicateDisplayMode = saved as 'group' | 'interleave';
      }
    } catch (e) {
      // ignore if localStorage unavailable
    }
  }

  ngOnDestroy(): void {
    this.userSub?.unsubscribe();
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
  
  // Load only locations for the selected site (progressive/cascading)
  private loadLocations(site: string) {
    this.filtersLoading = true;
    this.api.getDistinctLocations({ connectionKey: site }).subscribe({
      next: (locations: string[]) => {
        this.filterOptions = { locations: locations || [], dataTypes: [], testerTypes: [], dataTypeExt: [] };
        this.filtersLoading = false;
      },
      error: (err: unknown) => {
        console.error('Failed to load locations', err);
        this.filterOptions = { locations: [], dataTypes: [], testerTypes: [], dataTypeExt: [] };
        this.filtersLoading = false;
      }
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
    // start cascading by loading locations
    this.loadLocations(this.selectedSite);
  }

  addLotWaferPair() {
    if (this.lotWaferPairs.length >= this.maxLotWaferPairs) return;
    this.lotWaferPairs.push({ lot: null, wafer: null });
  }

  removeLotWaferPair(index: number) {
    if (this.lotWaferPairs.length <= 1) return;
    this.lotWaferPairs.splice(index, 1);
  }
  
  isLastPairFilled(): boolean {
    if (!this.lotWaferPairs.length) return false;
    const last = this.lotWaferPairs[this.lotWaferPairs.length - 1];
    // wafer is optional, consider last pair filled if lot has a value
    return !!(last && last.lot && String(last.lot).trim().length);
  }

  hasAtLeastOneLot(): boolean {
    return this.lotWaferPairs.some(p => p.lot && String(p.lot).trim().length > 0);
  }

  // legacy combined loader still available but not used for cascading
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
    // kept for full-site sender listing; prefer filtered sender loading in cascading flow
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

  // Load data types after location selected
  private loadDataTypes() {
    if (!this.selectedSite || !this.selectedLocation) {
      this.filterOptions = { locations: this.filterOptions?.locations ?? [], dataTypes: [], testerTypes: [], dataTypeExt: [] };
      return;
    }
    this.filtersLoading = true;
    this.api.getDistinctDataTypes({ connectionKey: this.selectedSite, location: this.selectedLocation }).subscribe({
      next: (dataTypes: string[]) => {
        this.filterOptions = { locations: this.filterOptions?.locations ?? [], dataTypes: dataTypes || [], testerTypes: [], dataTypeExt: [] };
        this.filtersLoading = false;
      },
      error: (err: unknown) => {
        console.error('Failed to load data types', err);
        this.filterOptions = { locations: this.filterOptions?.locations ?? [], dataTypes: [], testerTypes: [], dataTypeExt: [] };
        this.filtersLoading = false;
      }
    });
  }

  // Load tester types after data type selected
  private loadTesterTypes() {
    if (!this.selectedSite || !this.selectedLocation || !this.selectedDataType) {
      this.filterOptions = { locations: this.filterOptions?.locations ?? [], dataTypes: this.filterOptions?.dataTypes ?? [], testerTypes: [], dataTypeExt: [] };
      return;
    }
    this.filtersLoading = true;
    this.api.getDistinctTesterTypes({ connectionKey: this.selectedSite, location: this.selectedLocation, dataType: this.selectedDataType }).subscribe({
      next: (testerTypes: string[]) => {
        this.filterOptions = { locations: this.filterOptions?.locations ?? [], dataTypes: this.filterOptions?.dataTypes ?? [], testerTypes: testerTypes || [], dataTypeExt: [] };
        this.filtersLoading = false;
      },
      error: (err: unknown) => {
        console.error('Failed to load tester types', err);
        this.filterOptions = { locations: this.filterOptions?.locations ?? [], dataTypes: this.filterOptions?.dataTypes ?? [], testerTypes: [], dataTypeExt: [] };
        this.filtersLoading = false;
      }
    });
  }

  // Load senders filtered by all upstream selections and try to auto-resolve
  private loadSendersFiltered() {
    if (!this.selectedSite) return;
    this.sendersLoading = true;
    this.senderAutoResolved = false;
    const params: Record<string, any> = { connectionKey: this.selectedSite };
    if (this.selectedLocation) params['location'] = this.selectedLocation;
    if (this.selectedDataType) params['dataType'] = this.selectedDataType;
    if (this.selectedTesterType) params['testerType'] = this.selectedTesterType;
    this.api.getExternalSenders(params).subscribe({
      next: (senders: SenderOption[]) => {
        const opts = (senders || []).filter((s): s is SenderOption => !!s && s.idSender != null);
        this.senderOptions = opts;
        if (opts.length === 1) {
          this.selectedSenderId = opts[0].idSender ?? null;
          this.senderAutoResolved = true;
        } else {
          // preserve previous selection if still present
          if (this.selectedSenderId != null && !this.senderOptions.some(s => s.idSender === this.selectedSenderId)) {
            this.selectedSenderId = null;
          }
          this.senderAutoResolved = false;
        }
        this.refreshTestPhasesIfReady();
      },
      error: (err: unknown) => {
        console.error('Failed to load filtered senders', err);
        this.senderOptions = [];
        this.selectedSenderId = null;
        this.senderAutoResolved = false;
        this.sendersLoading = false;
      },
      complete: () => {
        this.sendersLoading = false;
      }
    });
  }

  public getSelectedSenderName(): string | null {
    if (this.selectedSenderId == null) {
      return null;
    }
    const selected = this.senderOptions.find(opt => opt.idSender === this.selectedSenderId);
    return selected?.name ?? null;
  }

  onLocationChanged() {
    this.clearDiscoveryState();
    // when location chosen, load data types
    this.loadDataTypes();
    // clear sender selection and reload filtered senders
    this.selectedSenderId = null;
    this.loadSendersFiltered();
  }

  onDataTypeChanged() {
    this.clearDiscoveryState();
    // when data type chosen, load tester types
    this.loadTesterTypes();
    this.selectedSenderId = null;
    this.loadSendersFiltered();
  }

  onTesterTypeChanged() {
    this.clearDiscoveryState();
    // when testerType chosen, reload senders filtered by all upstream
    this.selectedSenderId = null;
    this.loadSendersFiltered();
    this.refreshTestPhasesIfReady();
  }

  onSenderChanged() {
    this.clearDiscoveryState();
    // if user manually selected sender, it's not auto-resolved
    this.senderAutoResolved = false;
    this.refreshTestPhasesIfReady();
  }

  onStartDateChange(value: string) {
    if (!value) {
      this.startDate = null;
      return;
    }
    // value is from datetime-local (local time like 'yyyy-MM-ddTHH:mm'), create Date using local time
    // If seconds are missing, Date constructor will treat it as local without seconds.
    const parsed = new Date(value);
    if (isNaN(parsed.getTime())) {
      // Fallback: try appending seconds
      const tryWithSec = new Date(value + ':00');
      this.startDate = isNaN(tryWithSec.getTime()) ? null : tryWithSec;
    } else {
      this.startDate = parsed;
    }
  }

  onEndDateChange(value: string) {
    if (!value) {
      this.endDate = null;
      return;
    }
    const parsed = new Date(value);
    if (isNaN(parsed.getTime())) {
      const tryWithSec = new Date(value + ':00');
      this.endDate = isNaN(tryWithSec.getTime()) ? null : tryWithSec;
    } else {
      this.endDate = parsed;
    }
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
    // If lot/wafer provided (user role), allow searching by lot/wafer instead of date range/environment
    const hasRequired = Boolean(this.selectedSite && this.selectedLocation && this.selectedDataType && this.selectedTesterType && this.selectedSenderId != null);
    // require at least one LOT when user is non-admin (wafer optional)
    const hasLotWafer = this.lotWaferPairs.some(p => (p.lot && String(p.lot).trim().length > 0 && (p.wafer || true)));
    const hasLot = this.hasAtLeastOneLot();
    const hasDateRange = Boolean(this.startDate && this.endDate);
    // For non-admins, require at least one lot; for admins, require either lot/wafer or a date range
    if (!this.isAdmin) {
      return hasRequired && hasLot;
    }
    return hasRequired && (hasLotWafer || hasDateRange);
  }

  doPreview(page: number = 0) {
    if (!this.canSearch()) {
      this.toast.error('Select all required filters before searching.');
      return;
    }

    const site = this.selectedSite as string;
    const request: DiscoveryPreviewRequest = {
      site,
      startDate: this.formatStartDate(),
      endDate: this.formatEndDate(),
      // send arrays of lots and wafers where positions correspond
      lots: this.lotWaferPairs.map(p => p.lot ?? '').filter(Boolean) as string[] | null,
      wafers: this.lotWaferPairs.map(p => p.wafer ?? '').filter(Boolean) as string[] | null,
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
        const rawSql = response?.debugSql ?? null;
        this.previewDebugSql = rawSql && rawSql.trim().length ? rawSql.trim() : null;
        this.cachePreviewRows(this.previewRows);
        this.updateSelectAllState();
        this.recalculateSelectedCount();
        this.stepIndex = Math.max(this.stepIndex, 1);
        if (this.previewRows.length === 0 && this.previewTotal > 0 && this.previewPage > 0) {
          // If page is beyond total (race condition), reset to first page
          this.doPreview(0);
        }
        // After loading preview rows, fetch duplicate details for this page so the user sees full duplicate info in Preview
        try {
          const items = this.previewRows.map(r => ({ metadataId: r.metadataId ?? null, dataId: r.dataId ?? null }));
          this.previewDuplicatesMap.clear();
          this.api.previewDuplicates(this.selectedSenderId as number, { site, senderId: this.selectedSenderId, items }).subscribe({
            next: (dups) => {
              if (!dups || !dups.length) return;
              for (const d of dups) {
                const k = `${(d.metadataId ?? '').trim()}|${(d.dataId ?? '').trim()}`;
                if (k && k !== '|') this.previewDuplicatesMap.set(k, d);
              }
            },
            error: (err: unknown) => {
              // non-fatal: preview duplicate lookup failed, just log and continue
              console.error('Preview duplicate lookup failed', err);
            }
          });
        } catch (e) {
          console.error('Failed scheduling preview duplicate lookup', e);
        }
      },
      error: (err: unknown) => {
        console.error('Preview failed', err);
        this.toast.error('Discovery preview failed');
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

  stageSelected(force: boolean = false) {
    if (!this.selectedSite || !this.selectedSenderId) {
      this.toast.error('Site and sender must be selected.');
      return;
    }
    const payloads = this.collectSelectedPayloads();
    if (!payloads.length) {
      this.toast.error('Select at least one payload to stage.');
      return;
    }

    const body: StagePayloadRequestBody = {
      site: this.selectedSite,
      senderId: this.selectedSenderId,
      payloads,
      triggerDispatch: this.triggerDispatch,
      forceDuplicates: force
    };

    this.staging = true;
    this.api.stagePayloads(this.selectedSenderId, body).subscribe({
      next: (response: StagePayloadResponseBody) => {
        if (response?.requiresConfirmation && !force) {
          this.staging = false;
          this.openDuplicatePrompt(response, payloads.length);
          return;
        }
        this.finalizeStage(response, payloads.length);
      },
      error: (err: unknown) => {
        console.error('Staging failed', err);
        this.toast.error('Failed staging payloads');
        this.staging = false;
      },
      complete: () => {
        this.staging = false;
      }
    });
  }

  private finalizeStage(response: StagePayloadResponseBody, fallbackCount: number) {
    this.stageResponse = response;
    const stagedCount = response?.staged ?? fallbackCount;
    const duplicateCount = response?.duplicates ?? 0;
    const duplicateNote = duplicateCount > 0 ? ` (${duplicateCount} duplicate${duplicateCount === 1 ? '' : 's'})` : '';
    this.toast.success(`Staged ${stagedCount} payload${stagedCount === 1 ? '' : 's'}${duplicateNote}`);
    this.stepIndex = 2;
    this.refreshMonitoring();
  }

  private async openDuplicatePrompt(response: StagePayloadResponseBody, fallbackCount: number): Promise<void> {
    try {
      const result = await this.modal.openComponent(DuplicateWarningDialogComponent as any, { data: { currentUser: this.currentUser, duplicates: response.duplicatePayloads } as any });
      if (result) {
        // force staging
        this.stageSelected(true);
      } else {
        this.toast.info('Staging cancelled. No changes applied.');
        this.stageResponse = response;
      }
    } catch (err) {
      console.error('Duplicate prompt failed', err);
      // fallback to inline behavior
      this.stageResponse = response;
    }
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
    this.triggerDispatch = false;
  }

  copyDuplicatesToClipboard() {
    const duplicates = this.visibleDuplicatePayloads ?? [];
    if (!duplicates.length) {
      this.toast.info('No duplicate payloads to copy.');
      return;
    }
    const text = duplicates
      .map(dup => {
        const segments: string[] = [`${dup.metadataId},${dup.dataId}`];
        if (dup.lastRequestedBy) {
          segments.push(`lastBy=${dup.lastRequestedBy}`);
        }
        if (dup.lastRequestedAt) {
          segments.push(`lastAt=${this.formatDateTime(dup.lastRequestedAt)}`);
        }
        if (dup.previousStatus) {
          segments.push(`status=${dup.previousStatus}`);
        }
        if (dup.processedAt) {
          segments.push(`processedAt=${this.formatDateTime(dup.processedAt)}`);
        }
        return segments.join(' ');
      })
      .join('\n');
    navigator.clipboard.writeText(text).then(
      () => this.toast.success('Duplicate payload IDs copied.'),
      () => this.toast.error('Unable to copy duplicate payloads.')
    );
  }

  copyPreviewSql() {
    if (!this.previewDebugSql) {
      this.toast.info('No SQL to copy.');
      return;
    }
    navigator.clipboard.writeText(this.previewDebugSql).then(
      () => this.toast.success('Preview SQL copied.'),
      () => this.toast.error('Unable to copy SQL.')
    );
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
    this.previewDebugSql = null;
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

  setDuplicateDisplayMode(mode: 'group' | 'interleave') {
    this.duplicateDisplayMode = mode;
    try {
      localStorage.setItem('duplicateDisplayMode', mode);
    } catch (e) {
      // ignore failures (e.g., private mode)
    }
  }

  openDuplicateDetails(row: DiscoveryPreviewRow): void {
    const dup = this.previewDuplicateForRow(row);
    if (!dup) return;
    // reuse DuplicateWarningDialogComponent to show a single duplicate
    try {
      this.modal.openComponent<any>(
        // lazy reference to avoid import cycle in template compile
        // actual component imported at top of file earlier
        // @ts-ignore
        DuplicateWarningDialogComponent,
        { data: { currentUser: this.currentUser, duplicates: [dup] } }
      ).then(() => {});
    } catch (e) {
      console.error('Failed to open duplicate details modal', e);
    }
  }

  skipPreviewRow(row: DiscoveryPreviewRow): void {
    const key = this.rowKey(row);
    if (!key) return;
    // remove selection for this row and keep it visible; user can re-select later
    this.previewSelectedKeys.delete(key);
    this.previewRowCache.delete(key);
    this.previewRows = this.previewRows.filter(r => this.rowKey(r) !== key);
    this.updateSelectAllState();
    this.recalculateSelectedCount();
  }

  async stageSingleForce(row: DiscoveryPreviewRow): Promise<void> {
    if (!this.selectedSite || !this.selectedSenderId) {
      this.toast.error('Site and sender must be selected.');
      return;
    }
    const key = this.rowKey(row);
    if (!key) {
      this.toast.error('Invalid payload selected.');
      return;
    }
    if (!row.metadataId || !row.dataId) {
      this.toast.error('Row lacks metadataId or dataId.');
      return;
    }
    const body: StagePayloadRequestBody = {
      site: this.selectedSite,
      senderId: this.selectedSenderId,
      payloads: [{ metadataId: row.metadataId, dataId: row.dataId }],
      triggerDispatch: this.triggerDispatch,
      forceDuplicates: true
    };
    this.staging = true;
    this.api.stagePayloads(this.selectedSenderId, body).subscribe({
      next: (response: StagePayloadResponseBody) => {
        // remove the row from preview UI and selections
        this.previewSelectedKeys.delete(key);
        this.previewRowCache.delete(key);
        this.previewRows = this.previewRows.filter(r => this.rowKey(r) !== key);
        this.updateSelectAllState();
        this.recalculateSelectedCount();
        // use existing finalize logic to show toast and refresh monitoring
        this.finalizeStage(response, 1);
      },
      error: (err: unknown) => {
        console.error('Failed force-staging single payload', err);
        this.toast.error('Failed staging payload');
        this.staging = false;
      },
      complete: () => {
        this.staging = false;
      }
    });
  }

  previewDuplicateForRow(row: DiscoveryPreviewRow): import('../api/backend.service').DuplicatePayloadInfo | null {
    const key = this.rowKey(row);
    if (!key) return null;
    return this.previewDuplicatesMap.get(key) ?? null;
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

  exportPreviewCsv(selectedOnly: boolean = false) {
    const rows = selectedOnly ? this.collectSelectedPreviewRows() : [...this.previewRows];
    if (!rows.length) {
      this.toast.info(selectedOnly ? 'No selected payloads to export.' : 'No preview rows to export.');
      return;
    }

    const csvRows = rows.map(row => [
      row.metadataId ?? '',
      row.dataId ?? '',
      row.lot ?? '',
      row.wafer ?? '',
      row.originalFileName ?? '',
      this.formatDateTime(row.endTime)
    ]);

    const fileName = `discovery_${selectedOnly ? 'selected' : 'page'}`;
    const exported = this.csv.download({
      filename: fileName,
      headers: ['Metadata ID', 'Data ID', 'Lot', 'Wafer', 'File', 'End Time'],
      rows: csvRows
    });

    if (exported) {
      this.toast.success(`Exported ${rows.length} payload${rows.length === 1 ? '' : 's'} to CSV.`);
    } else {
      this.toast.error('Unable to create CSV export.');
    }
  }

  exportStageRecordsCsv(): void {
    if (!this.stageRecords.length) {
      this.toast.info('No staged records to export.');
      return;
    }

    const rows = this.stageRecords.map(record => [
      record.site ?? '',
      record.senderId ?? '',
      record.metadataId ?? '',
      record.dataId ?? '',
      record.status ?? '',
      record.lastRequestedBy ?? record.stagedBy ?? '',
      this.formatDateTime(record.lastRequestedAt),
      this.formatDateTime(record.updatedAt),
      this.formatDateTime(record.processedAt ?? null),
      record.errorMessage ?? ''
    ]);

    const exported = this.csv.download({
      filename: 'stage-records',
      headers: ['Site', 'Sender ID', 'Metadata ID', 'Data ID', 'Status', 'Owner', 'Last Requested At', 'Updated At', 'Processed At', 'Error'],
      rows
    });

    if (exported) {
      this.toast.success(`Exported ${rows.length} record${rows.length === 1 ? '' : 's'} to CSV.`);
    } else {
      this.toast.error('Unable to create staged records export.');
    }
  }

  exportDuplicateCsv(): void {
    const duplicates = this.visibleDuplicatePayloads ?? [];
    if (!duplicates.length) {
      this.toast.info('No duplicate payloads to export.');
      return;
    }

    const rows = duplicates.map(dup => [
      dup.metadataId ?? '',
      dup.dataId ?? '',
      dup.previousStatus ?? '',
      dup.lastRequestedBy ?? '',
      this.formatDateTime(dup.lastRequestedAt),
      dup.stagedBy ?? '',
      this.formatDateTime(dup.stagedAt),
      this.formatDateTime(dup.processedAt)
    ]);

    const exported = this.csv.download({
      filename: 'duplicate-payloads',
      headers: ['Metadata ID', 'Data ID', 'Previous Status', 'Last Requested By', 'Last Requested At', 'Staged By', 'Staged At', 'Processed At'],
      rows
    });

    if (exported) {
      this.toast.success(`Exported ${rows.length} duplicate payload${rows.length === 1 ? '' : 's'} to CSV.`);
    } else {
      this.toast.error('Unable to create duplicate payload export.');
    }
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

  private collectSelectedPreviewRows(): DiscoveryPreviewRow[] {
    const rows: DiscoveryPreviewRow[] = [];
    for (const key of this.previewSelectedKeys) {
      const row = this.previewRowCache.get(key);
      if (row) {
        rows.push(row);
      }
    }
    return rows;
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
        this.toast.error('Failed loading stage status');
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
        this.toast.error('Failed loading stage records');
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

  /**
   * Duplicates visible to the current view. If the user is admin, show all duplicates.
   * For non-admins, only show duplicates that involve the current user (staged_by or last_requested_by).
   */
  get visibleDuplicatePayloads() {
    const all = this.stageResponse?.duplicatePayloads ?? [];
    if (this.isAdmin) return all;
    if (!this.currentUser) return [];
    const me = String(this.currentUser).toLowerCase();
    // Non-admin: show duplicates that were staged/last-requested by the user OR duplicates that match metadata/data present in the current staged records.
    const recordKeys = new Set((this.stageRecords || []).map(r => `${(r.metadataId||'').trim()}|${(r.dataId||'').trim()}`));
    return all.filter(d => {
      const u = (d.lastRequestedBy || d.stagedBy || '').toString().toLowerCase();
      if (u && u === me) return true;
      const key = `${(d.metadataId||'').trim()}|${(d.dataId||'').trim()}`;
      return recordKeys.has(key);
    });
  }

  /**
   * Merge staged records and visible duplicates into a single chronological list for interleaving.
   * Each item is annotated with a __type: 'record' | 'dup' so the template can render accordingly.
   */
  get mergedStageAndDuplicateRows() {
    type MergedRow = { __type: 'record' | 'dup'; item: any; ts: Date | null };
    const records: MergedRow[] = (this.stageRecords || []).map(r => ({ __type: 'record', item: r as any, ts: this._pickTimestampFromRecord(r) }));
    const dups: MergedRow[] = (this.visibleDuplicatePayloads || []).map(d => ({ __type: 'dup', item: d as any, ts: this._pickTimestampFromDup(d) }));
    const combined: MergedRow[] = records.concat(dups);
    combined.sort((a, b) => {
      const ta = a.ts ? a.ts.getTime() : 0;
      const tb = b.ts ? b.ts.getTime() : 0;
      // newest first
      return tb - ta;
    });
    return combined;
  }

  private _pickTimestampFromRecord(r: any): Date | null {
    try {
      if (r.lastRequestedAt) return new Date(r.lastRequestedAt);
      if (r.updatedAt) return new Date(r.updatedAt);
      if (r.processedAt) return new Date(r.processedAt);
    } catch (e) {}
    return null;
  }

  private _pickTimestampFromDup(d: any): Date | null {
    try {
      if (d.lastRequestedAt) return new Date(d.lastRequestedAt);
      if (d.stagedAt) return new Date(d.stagedAt);
      if (d.processedAt) return new Date(d.processedAt);
    } catch (e) {}
    return null;
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
    // Preserve the time component if user provided it; otherwise default to start of day
    const hasTime = this.startDate.getHours() !== 0 || this.startDate.getMinutes() !== 0 || this.startDate.getSeconds() !== 0;
    const timePart = hasTime ? formatDate(this.startDate, 'HH:mm:ss', 'en-US') : '00:00:00';
    return `${formatDate(this.startDate, 'yyyy-MM-dd', 'en-US')} ${timePart}`;
  }

  private formatEndDate(): string | null {
    if (!this.endDate) {
      return null;
    }
    // Preserve the time component if user provided it; otherwise default to end of day
    const hasTime = this.endDate.getHours() !== 0 || this.endDate.getMinutes() !== 0 || this.endDate.getSeconds() !== 0;
    const timePart = hasTime ? formatDate(this.endDate, 'HH:mm:ss', 'en-US') : '23:59:59';
    return `${formatDate(this.endDate, 'yyyy-MM-dd', 'en-US')} ${timePart}`;
  }

  private formatDateTime(value: string | Date | null | undefined): string {
    if (!value) {
      return '';
    }
    const date = value instanceof Date ? value : new Date(value);
    if (isNaN(date.getTime())) {
      return String(value ?? '');
    }
    return formatDate(date, this.uiDateFormat, 'en-US');
  }
}
