import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription, timer } from 'rxjs';
import { BackendService, StageStatus, StageUserStatus } from '../api/backend.service';

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
  ready: number;
  enqueued: number;
  failed: number;
  total: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  private refreshSub?: Subscription;
  private autoRefreshSub?: Subscription;

  loading = false;
  errorMessage: string | null = null;
  statuses: StageStatus[] = [];
  lastUpdated: Date | null = null;

  readonly refreshIntervalMs = 60_000;

  constructor(private api: BackendService) {}

  ngOnInit(): void {
    this.refresh();
    this.autoRefreshSub = timer(this.refreshIntervalMs, this.refreshIntervalMs).subscribe(() => this.refresh(false));
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
    return this.lastUpdated.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
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
        return {
          label: `${status.site || 'Unknown'} · ${status.senderId || 'N/A'}`,
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
}
