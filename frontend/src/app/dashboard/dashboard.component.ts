import { CommonModule, formatDate } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
// removed MatDialogModule as we migrated to inline dialog components
import { ToastService } from '../ui/toast.service';
import { AuthService } from '../auth/auth.service';
import { firstValueFrom, Subscription, timer } from 'rxjs';
import { filter, take } from 'rxjs/operators';
import { BackendService, DispatchResponse, SenderOption, StageStatus, StageUserStatus } from '../api/backend.service';
import { DashboardDetailDialogComponent, DashboardDetailDialogData, DashboardDetailColumn } from './dashboard-detail-dialog.component';
import { ModalService } from '../ui/modal.service';
import { SenderLookupDialogComponent } from '../sender-lookup.dialog';
import { IconComponent } from '../ui/icon.component';
import { ButtonComponent } from '../ui/button.component';
import { CardComponent } from '../ui/card.component';
import { TableComponent } from '../ui/table.component';
import { BadgeComponent } from '../ui/badge.component';
import { TooltipDirective } from '../ui/tooltip.directive';

interface DashboardAggregate {
  total: number;
  ready: number;
  enqueued: number;
  failed: number;
  completed: number;
  backlog: number;
  activeSenders: number;
}

interface DashboardSenderSummary {
  senderId: number;
  senderLabel: string;
  senderName: string | null;
  total: number;
  ready: number;
  enqueued: number;
  failed: number;
  completed: number;
  backlog: number;
  alert: boolean;
  users: StageUserStatus[];
}

interface DashboardSiteSummary extends DashboardAggregate {
  site: string;
  senders: DashboardSenderSummary[];
  alerts: boolean;
}

interface BacklogSeries {
  label: string;
  site: string;
  senderId: number;
  senderLabel: string;
  ready: number;
  enqueued: number;
  failed: number;
  total: number;
}

type GlobalMetricName = 'activeSenders' | 'ready' | 'enqueued' | 'failed' | 'completed' | 'backlog';

interface GlobalDetailRow extends Record<string, string | number | null | undefined> {
  site: string;
  sender: string;
  senderId: number | null;
  ready: number;
  enqueued: number;
  failed: number;
  completed: number;
  backlog: number;
  active: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, IconComponent, ButtonComponent, CardComponent, TooltipDirective],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  private refreshSub?: Subscription;
  private autoRefreshSub?: Subscription;
  private senderCatalog = new Map<string, Map<number, string>>();
  private senderLookupInFlight = new Set<string>();

  loading = false;
  errorMessage: string | null = null;
  statuses: StageStatus[] = [];
  lastUpdated: Date | null = null;

  readonly refreshIntervalMs = 60_000;
  readonly uiDateFormat = 'yyyy-MM-dd HH:mm:ss';
  private readonly globalMetricConfig: Record<GlobalMetricName, { label: string; valueKey: keyof GlobalDetailRow; description: string; filterZero?: boolean }> = {
    activeSenders: {
      label: 'Active Senders',
      valueKey: 'active',
      description: 'Senders currently tracked across all connections.',
      filterZero: false
    },
    ready: {
      label: 'Ready Payloads',
      valueKey: 'ready',
      description: 'Payloads staged and ready to dispatch grouped by site and sender.',
      filterZero: true
    },
    enqueued: {
      label: 'Enqueued Payloads',
      valueKey: 'enqueued',
      description: 'Payloads already enqueued for processing.',
      filterZero: true
    },
    failed: {
      label: 'Failed Payloads',
      valueKey: 'failed',
      description: 'Payloads requiring attention due to failures.',
      filterZero: true
    },
    completed: {
      label: 'Completed Payloads',
      valueKey: 'completed',
      description: 'Payloads successfully processed.',
      filterZero: true
    },
    backlog: {
      label: 'Backlog Payloads',
      valueKey: 'backlog',
      description: 'Outstanding backlog totals accrued from ready, enqueued, and failed payloads.',
      filterZero: true
    }
  };

  // dialog handled by ModalService

  constructor(private api: BackendService, private toast: ToastService, private modal: ModalService, private auth: AuthService) {}

  async openSenderLookup(site?: string) {
    try {
      const items = await firstValueFrom(this.api.lookupSenders({ site: site ?? 'default' }).pipe());
      const result = await this.modal.openComponent(SenderLookupDialogComponent as any, { items } as any);
      if (result) {
        // user selected an item — show toast (caller can implement full wiring later)
        this.toast.info(`Selected sender: ${JSON.stringify(result)}`);
      }
    } catch (err) {
      console.error('Sender lookup failed', err);
      this.toast.error('Sender lookup failed');
    }
  }

  ngOnInit(): void {
    // Wait until AuthService emits a user before performing dashboard refreshes.
    // This avoids making authenticated requests before the access token is set
    // (prevents race conditions where the interceptor sees no token and requests 401).
    // Use `filter`+`take(1)` so the subscribe callback runs only once and we don't
    // need to manually unsubscribe (avoids TDZ issues when subscribe emits synchronously).
    this.auth.user$.pipe(
      filter(user => !!user),
      take(1)
    ).subscribe(() => {
      this.refresh();
      this.autoRefreshSub = timer(this.refreshIntervalMs, this.refreshIntervalMs).subscribe(() => this.refresh(false));
    });
  }

  ngOnDestroy(): void {
    this.refreshSub?.unsubscribe();
    this.autoRefreshSub?.unsubscribe();
  }

  refresh(showLoader: boolean = true): void {
    if (showLoader) {
      this.loading = true;
    }
    this.errorMessage = null;
    this.refreshSub?.unsubscribe();
    this.refreshSub = this.api.getStageStatus().subscribe({
      next: statuses => {
        this.statuses = (statuses ?? []).map(status => ({
          site: status?.site ?? 'Unknown',
          senderId: status?.senderId ?? 0,
          total: status?.total ?? 0,
          ready: status?.ready ?? 0,
          enqueued: status?.enqueued ?? 0,
          failed: status?.failed ?? 0,
          completed: status?.completed ?? 0,
          users: (status?.users ?? []).map(user => this.normalizeUserStatus(user))
        } as StageStatus));
        this.ensureSenderNames(this.statuses);
        this.lastUpdated = new Date();
        this.loading = false;
      },
      error: err => {
        console.error('Dashboard refresh failed', err);
        this.errorMessage = 'Unable to load dashboard data.';
        this.statuses = [];
        this.loading = false;
      }
    });
  }

  get hasData(): boolean {
    return this.statuses.length > 0;
  }

  get globalSummary(): DashboardAggregate | null {
    if (!this.statuses.length) {
      return null;
    }
    return this.aggregate(this.statuses);
  }

  get siteSummaries(): DashboardSiteSummary[] {
    if (!this.statuses.length) {
      return [];
    }
    const map = new Map<string, DashboardSiteSummary>();
    for (const status of this.statuses) {
      const siteKey = status.site || 'Unknown';
      let summary = map.get(siteKey);
      if (!summary) {
        summary = {
          site: siteKey,
          total: 0,
          ready: 0,
          enqueued: 0,
          failed: 0,
          completed: 0,
          backlog: 0,
          activeSenders: 0,
          senders: [],
          alerts: false
        };
        map.set(siteKey, summary);
      }

      const senderSummary: DashboardSenderSummary = {
        senderId: status.senderId,
        senderLabel: this.formatSenderLabel(siteKey, status.senderId),
        senderName: this.lookupSenderName(siteKey, status.senderId),
        total: status.total,
        ready: status.ready,
        enqueued: status.enqueued,
        failed: status.failed,
        completed: status.completed,
        backlog: status.ready + status.enqueued + status.failed,
        alert: status.ready > 0 || status.failed > 0,
        users: status.users ?? []
      };

      summary.senders.push(senderSummary);
      summary.total += senderSummary.total;
      summary.ready += senderSummary.ready;
      summary.enqueued += senderSummary.enqueued;
      summary.failed += senderSummary.failed;
      summary.completed += senderSummary.completed;
      summary.backlog += senderSummary.backlog;
      summary.activeSenders += 1;
      summary.alerts = summary.alerts || senderSummary.alert;
    }

    const siteResults = Array.from(map.values());
    siteResults.forEach(site => {
      site.senders.sort((a, b) => {
        if (b.backlog !== a.backlog) {
          return b.backlog - a.backlog;
        }
        return a.senderId - b.senderId;
      });
    });

    siteResults.sort((a, b) => {
      if (b.backlog !== a.backlog) {
        return b.backlog - a.backlog;
      }
      if (b.failed !== a.failed) {
        return b.failed - a.failed;
      }
      return a.site.localeCompare(b.site);
    });

    return siteResults;
  }

  formatUpdated(): string {
    if (!this.lastUpdated) {
      return 'Never';
    }
    return formatDate(this.lastUpdated, this.uiDateFormat, 'en-US');
  }

  get backlogSeries(): BacklogSeries[] {
    if (!this.statuses.length) {
      return [];
    }
    const series: BacklogSeries[] = this.statuses
      .map(status => {
        const ready = status.ready ?? 0;
        const enqueued = status.enqueued ?? 0;
        const failed = status.failed ?? 0;
        const siteLabel = status.site || 'Unknown';
        const senderLabel = this.formatSenderLabel(siteLabel, status.senderId ?? 0);
        return {
          site: siteLabel,
          senderId: status.senderId ?? 0,
          senderLabel,
          label: `${siteLabel} · ${senderLabel}`,
          ready,
          enqueued,
          failed,
          total: ready + enqueued + failed
        };
      })
      .filter(item => item.total > 0)
      .sort((a, b) => b.total - a.total)
      .slice(0, 5);
    return series;
  }

  barWidth(value: number, total: number): string {
    if (!total || total <= 0) {
      return '0%';
    }
    const pct = Math.max(0, Math.min(100, (value / total) * 100));
    return pct.toFixed(1) + '%';
  }

  showSegmentLabel(value: number, total: number): boolean {
    if (!total || total <= 0) {
      return false;
    }
    return (value / total) >= 0.12;
  }

  segmentTooltip(label: string, value: number, total: number): string {
    if (!total || total <= 0) {
      return `${label}: ${value}`;
    }
    const pct = (value / total) * 100;
    return `${label}: ${value} (${pct.toFixed(1)}%)`;
  }

  openGlobalDetail(metric: GlobalMetricName): void {
    const config = this.globalMetricConfig[metric];
    const rows: GlobalDetailRow[] = (this.statuses || []).map(status => {
      const ready = status.ready ?? 0;
      const enqueued = status.enqueued ?? 0;
      const failed = status.failed ?? 0;
      const completed = status.completed ?? 0;
      const siteValue = status.site || 'Unknown';
      const senderIdValue = status.senderId ?? null;
      return {
        site: siteValue,
        sender: this.formatSenderLabel(siteValue, senderIdValue),
        senderId: this.normalizeSenderId(senderIdValue),
        ready,
        enqueued,
        failed,
        completed,
        backlog: ready + enqueued + failed,
        active: 1
      };
    });

    const filtered = config.filterZero
      ? rows.filter(row => this.metricValue(row, config.valueKey) > 0)
      : [...rows];
    const sorted = filtered.sort((a, b) => this.metricValue(b, config.valueKey) - this.metricValue(a, config.valueKey));

    const dialogData: DashboardDetailDialogData = {
      title: config.label,
      description: config.description,
      columns: this.globalDetailColumns(metric),
      rows: sorted
    };

    if (metric === 'ready' && sorted.some(row => !this.isReadyActionHidden(row))) {
      dialogData.rowActions = [
        {
          key: 'enqueue',
          label: 'Enqueue',
          icon: 'play_circle',
          color: 'primary',
          tooltip: 'Enqueue ready payloads for this sender',
          handler: row => this.enqueueReady(row as GlobalDetailRow),
          disabled: row => this.isReadyActionDisabled(row as GlobalDetailRow),
          hidden: row => this.isReadyActionHidden(row as GlobalDetailRow)
        }
      ];
      dialogData.rowKey = row => this.rowKeyForGlobalRow(row as GlobalDetailRow);
    }

    this.openDetailDialog(dialogData);
  }

  openBacklogDetail(series: BacklogSeries): void {
    const status = this.statuses.find(s => (s.site || 'Unknown') === series.site && (s.senderId ?? 0) === series.senderId);
    if (status?.users?.length) {
      const rows = status.users.map(user => ({
        user: user.username || 'unknown',
        ready: user.ready ?? 0,
        enqueued: user.enqueued ?? 0,
        failed: user.failed ?? 0,
        completed: user.completed ?? 0,
        total: (user.ready ?? 0) + (user.enqueued ?? 0) + (user.failed ?? 0)
      }));
      const dialogData: DashboardDetailDialogData = {
        title: `${series.site} · ${series.senderLabel} backlog contributors`,
        description: `${series.site} · ${series.senderLabel}`,
        columns: [
          { key: 'user', label: 'User' },
          { key: 'ready', label: 'Ready', align: 'end' },
          { key: 'enqueued', label: 'Enqueued', align: 'end' },
          { key: 'failed', label: 'Failed', align: 'end' },
          { key: 'completed', label: 'Completed', align: 'end' },
          { key: 'total', label: 'Total Active', align: 'end' }
        ],
        rows
      };
      this.openDetailDialog(dialogData);
      return;
    }

    const rows = [
      { metric: 'Ready', count: series.ready },
      { metric: 'Enqueued', count: series.enqueued },
      { metric: 'Failed', count: series.failed },
      { metric: 'Backlog', count: series.total }
    ];
    this.openDetailDialog({
      title: `${series.site} · ${series.senderLabel} backlog totals`,
      description: `${series.site} · ${series.senderLabel}`,
      columns: [
        { key: 'metric', label: 'Metric' },
        { key: 'count', label: 'Count', align: 'end' }
      ],
      rows
    });
  }

  openSiteDetail(site: DashboardSiteSummary): void {
    const rows = site.senders.map(sender => ({
      sender: sender.senderLabel,
      ready: sender.ready,
      enqueued: sender.enqueued,
      failed: sender.failed,
      completed: sender.completed,
      backlog: sender.backlog
    }));
    this.openDetailDialog({
      title: `${site.site} · sender overview`,
      description: `${site.activeSenders} active sender${site.activeSenders === 1 ? '' : 's'} tracked for this site.`,
      columns: [
        { key: 'sender', label: 'Sender' },
        { key: 'ready', label: 'Ready', align: 'end' },
        { key: 'enqueued', label: 'Enqueued', align: 'end' },
        { key: 'failed', label: 'Failed', align: 'end' },
        { key: 'completed', label: 'Completed', align: 'end' },
        { key: 'backlog', label: 'Backlog', align: 'end' }
      ],
      rows
    });
  }

  openSenderDetail(site: DashboardSiteSummary, sender: DashboardSenderSummary): void {
    const status = this.statuses.find(s => (s.site || 'Unknown') === site.site && (s.senderId ?? 0) === sender.senderId);
    const users = status?.users ?? [];
    const rows = users.map(user => ({
      user: user.username || 'unknown',
      ready: user.ready ?? 0,
      enqueued: user.enqueued ?? 0,
      failed: user.failed ?? 0,
      completed: user.completed ?? 0,
      lastRequestedAt: user.lastRequestedAt ? formatDate(user.lastRequestedAt, this.uiDateFormat, 'en-US') : '—'
    }));

    this.openDetailDialog({
      title: `${site.site} · ${sender.senderLabel} activity`,
      description: 'User-level contribution for this sender.',
      columns: [
        { key: 'user', label: 'User' },
        { key: 'ready', label: 'Ready', align: 'end' },
        { key: 'enqueued', label: 'Enqueued', align: 'end' },
        { key: 'failed', label: 'Failed', align: 'end' },
        { key: 'completed', label: 'Completed', align: 'end' },
        { key: 'lastRequestedAt', label: 'Last Requested', align: 'end' }
      ],
      rows
    });
  }

  async openSenderSamplePreview(site: DashboardSiteSummary, sender: DashboardSenderSummary): Promise<void> {
    if (!site || !sender || sender.senderId == null) {
      this.toast.error('Unable to determine site or sender to preview.');
      return;
    }
    try {
      const req = { site: site.site, page: 0, size: 10 } as any;
      const resp = await firstValueFrom(this.api.previewDiscovery(sender.senderId, req));
      const rows = (resp?.items || []).map(item => ({
        metadataId: item.metadataId ?? '—',
        dataId: item.dataId ?? '—',
        lot: item.lot ?? '—',
        wafer: item.wafer ?? '—',
        file: item.originalFileName ?? '—',
        endTime: item.endTime ?? '—'
      }));
      const dialogData: DashboardDetailDialogData = {
        title: `${site.site} · ${sender.senderLabel} · sample files`,
        description: `Sample metadata from ${site.site} for sender ${sender.senderLabel}`,
        columns: [
          { key: 'metadataId', label: 'Metadata ID' },
          { key: 'dataId', label: 'Data ID' },
          { key: 'lot', label: 'Lot' },
          { key: 'wafer', label: 'Wafer' },
          { key: 'file', label: 'File' },
          { key: 'endTime', label: 'End Time' }
        ],
        rows
      };
      await this.openDetailDialog(dialogData);
    } catch (err) {
      console.error('Failed to preview sender sample files', err);
      this.toast.error('Failed to load sample files for preview.');
    }
  }

  private async openDetailDialog(data: DashboardDetailDialogData): Promise<void> {
    try {
      await this.modal.openComponent(DashboardDetailDialogComponent, { data });
    } catch (err) {
      console.error('Failed to open detail dialog', err);
    }
  }

  private async enqueueReady(row: GlobalDetailRow): Promise<void> {
    const senderId = this.normalizeSenderId(row.senderId);
    const site = row.site;
    if (!site || senderId === null) {
      this.toast.error('Unable to determine site or sender for enqueue request.');
      return;
    }
    const readyCount = Number(row.ready ?? 0);
    const limit = Number.isFinite(readyCount) && readyCount > 0 ? Math.floor(readyCount) : undefined;
    try {
  const response: DispatchResponse = await firstValueFrom(this.api.dispatchSender(senderId, { site, senderId, limit }));
  const dispatched = response.dispatched ?? 0;
      if (dispatched > 0) {
        this.toast.success(`Enqueued ${dispatched} payload${dispatched === 1 ? '' : 's'} for ${row.sender}`);
        row.ready = Math.max(0, (row.ready ?? 0) - dispatched);
        row.backlog = Math.max(0, (row.backlog ?? 0) - dispatched);
        row.enqueued = (row.enqueued ?? 0) + dispatched;
      } else {
        this.toast.info(`No payloads enqueued for ${row.sender}`);
      }
      this.refresh(false);
    } catch (err) {
      console.error(`Failed to enqueue ready payloads for ${site} sender ${senderId}`, err);
      this.toast.error(`Failed to enqueue ${row.sender}. See console for details.`);
    }
  }

  private isReadyActionHidden(row: GlobalDetailRow): boolean {
    const readyValue = Number(row.ready ?? 0);
    return !Number.isFinite(readyValue) || readyValue <= 0;
  }

  private isReadyActionDisabled(row: GlobalDetailRow): boolean {
    if (this.isReadyActionHidden(row)) {
      return true;
    }
    const site = row.site;
    if (!site || site === 'Unknown') {
      return true;
    }
    const senderId = this.normalizeSenderId(row.senderId);
    return senderId === null;
  }

  private rowKeyForGlobalRow(row: GlobalDetailRow): string {
    const senderPart = row.senderId != null ? String(row.senderId) : row.sender;
    return `${row.site || 'Unknown'}::${senderPart}`;
  }

  private normalizeSenderId(value: number | string | null | undefined): number | null {
    if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
      return value;
    }
    if (typeof value === 'string') {
      const parsed = Number(value);
      if (Number.isFinite(parsed) && parsed > 0) {
        return parsed;
      }
    }
    return null;
  }

  private globalDetailColumns(metric: GlobalMetricName): DashboardDetailColumn[] {
    if (metric === 'activeSenders') {
      return [
        { key: 'site', label: 'Site' },
        { key: 'sender', label: 'Sender' },
        { key: 'active', label: 'Active', align: 'end' }
      ];
    }

    const columns: DashboardDetailColumn[] = [
      { key: 'site', label: 'Site' },
      { key: 'sender', label: 'Sender' }
    ];

    const metricColumns: DashboardDetailColumn[] = [
      { key: 'ready', label: 'Ready', align: 'end' },
      { key: 'enqueued', label: 'Enqueued', align: 'end' },
      { key: 'failed', label: 'Failed', align: 'end' },
      { key: 'completed', label: 'Completed', align: 'end' },
      { key: 'backlog', label: 'Backlog', align: 'end' }
    ];

    const prioritized = [...metricColumns];
    const valueKey = this.globalMetricConfig[metric].valueKey;
    const valueIndex = prioritized.findIndex(col => col.key === valueKey);
    if (valueIndex > 0) {
      const [col] = prioritized.splice(valueIndex, 1);
      prioritized.unshift(col);
    }
    return [...columns, ...prioritized];
  }

  private aggregate(statuses: StageStatus[]): DashboardAggregate {
    return statuses.reduce<DashboardAggregate>((acc, status) => {
      acc.total += status.total;
      acc.ready += status.ready;
      acc.enqueued += status.enqueued;
      acc.failed += status.failed;
      acc.completed += status.completed;
      acc.backlog += status.ready + status.enqueued + status.failed;
      acc.activeSenders += 1;
      return acc;
    }, { total: 0, ready: 0, enqueued: 0, failed: 0, completed: 0, backlog: 0, activeSenders: 0 });
  }

  private normalizeUserStatus(user: StageUserStatus | undefined): StageUserStatus {
    return {
      username: user?.username ?? 'unknown',
      total: user?.total ?? 0,
      ready: user?.ready ?? 0,
      enqueued: user?.enqueued ?? 0,
      failed: user?.failed ?? 0,
      completed: user?.completed ?? 0,
      lastRequestedAt: user?.lastRequestedAt ?? null
    };
  }

  private metricValue(row: GlobalDetailRow, key: keyof GlobalDetailRow): number {
    const value = row[key];
    if (typeof value === 'number') {
      return value;
    }
    const parsed = Number(value ?? 0);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private ensureSenderNames(statuses: StageStatus[]): void {
    const sitesNeedingLookup = new Set<string>();
    for (const status of statuses) {
      const siteKey = status.site || 'Unknown';
      const senderId = status.senderId ?? null;
      if (!siteKey || senderId === null || senderId === undefined) {
        continue;
      }
      const cached = this.senderCatalog.get(siteKey);
      if (!cached || !cached.has(senderId)) {
        sitesNeedingLookup.add(siteKey);
      }
    }

    for (const site of sitesNeedingLookup) {
      if (this.senderLookupInFlight.has(site)) {
        continue;
      }
      this.senderLookupInFlight.add(site);
      this.api.getExternalSenders({ connectionKey: site }).subscribe({
        next: senders => this.storeSenderOptions(site, senders),
        error: err => {
          console.error(`Failed to load sender names for ${site}`, err);
          this.senderCatalog.set(site, this.senderCatalog.get(site) ?? new Map());
        },
        complete: () => {
          this.senderLookupInFlight.delete(site);
        }
      });
    }
  }

  private storeSenderOptions(site: string, senders: SenderOption[] | null | undefined): void {
    const map = new Map<number, string>();
    (senders || []).forEach(sender => {
      if (sender && sender.idSender != null) {
        const name = (sender.name ?? '').trim();
        map.set(sender.idSender, name);
      }
    });
    this.senderCatalog.set(site, map);
  }

  private lookupSenderName(site: string, senderId: number | null | undefined): string | null {
    if (!site || senderId === null || senderId === undefined) {
      return null;
    }
    const map = this.senderCatalog.get(site);
    return map?.get(senderId) ?? null;
  }

  formatSenderLabel(site: string, senderId: number | string | null | undefined): string {
    if (senderId === null || senderId === undefined || senderId === '') {
      return 'N/A';
    }
    const id = typeof senderId === 'number' ? senderId : Number(senderId);
    if (!Number.isNaN(id)) {
      const name = this.lookupSenderName(site, id);
      if (name && name.trim().length) {
        return `${id}-${name.trim()}`;
      }
      return String(id);
    }
    return String(senderId);
  }
}
