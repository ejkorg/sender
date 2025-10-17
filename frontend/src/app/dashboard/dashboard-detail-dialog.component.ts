import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable, isObservable } from 'rxjs';

export interface DashboardDetailColumn {
  key: string;
  label: string;
  align?: 'start' | 'end' | 'center';
}

export interface DashboardDetailDialogAction {
  key: string;
  label: string;
  icon?: string;
  color?: 'primary' | 'accent' | 'warn';
  tooltip?: string;
  handler?: (row: Record<string, string | number | null | undefined>) => void | Promise<void> | Observable<unknown>;
  disabled?: (row: Record<string, string | number | null | undefined>) => boolean;
  hidden?: (row: Record<string, string | number | null | undefined>) => boolean;
}

export interface DashboardDetailDialogData {
  title: string;
  description?: string;
  columns: DashboardDetailColumn[];
  rows: Array<Record<string, string | number | null | undefined>>;
  rowActions?: DashboardDetailDialogAction[];
  rowKey?: (row: Record<string, string | number | null | undefined>) => string;
}

@Component({
  selector: 'app-dashboard-detail-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './dashboard-detail-dialog.component.html',
  styleUrls: ['./dashboard-detail-dialog.component.css']
})
export class DashboardDetailDialogComponent {
  private executing = new Set<string>();

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: DashboardDetailDialogData,
    private dialogRef: MatDialogRef<DashboardDetailDialogComponent>,
    private cdr: ChangeDetectorRef
  ) {}

  get hasRows(): boolean {
    return (this.data?.rows?.length ?? 0) > 0;
  }

  get hasRowActions(): boolean {
    return !!(this.data?.rowActions && this.data.rowActions.length);
  }

  formatCell(value: string | number | null | undefined): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return String(value);
  }

  rowKey(row: Record<string, string | number | null | undefined>): string {
    if (this.data?.rowKey) {
      try {
        const key = this.data.rowKey(row);
        if (key) {
          return key;
        }
      } catch (err) {
        console.warn('Failed to resolve row key', err);
      }
    }
    return JSON.stringify(row);
  }

  isActionHidden(action: DashboardDetailDialogAction, row: Record<string, string | number | null | undefined>): boolean {
    return !!action.hidden?.(row);
  }

  isActionDisabled(action: DashboardDetailDialogAction, row: Record<string, string | number | null | undefined>): boolean {
    const key = this.rowKey(row);
    if (this.executing.has(key)) {
      return true;
    }
    return !!action.disabled?.(row);
  }

  isExecuting(row: Record<string, string | number | null | undefined>): boolean {
    return this.executing.has(this.rowKey(row));
  }

  async triggerAction(action: DashboardDetailDialogAction, row: Record<string, string | number | null | undefined>): Promise<void> {
    if (!action.handler) {
      return;
    }
    const key = this.rowKey(row);
    this.executing.add(key);
    this.cdr.markForCheck();
    try {
      const result = action.handler(row);
      if (isObservable(result)) {
        result.subscribe({
          error: err => {
            console.error('Dashboard detail action observable failed', err);
            this.completeAction(key);
          },
          complete: () => this.completeAction(key)
        });
        return;
      }
      if (this.isPromise(result)) {
        await result;
      }
    } catch (err) {
      console.error('Dashboard detail action failed', err);
      this.completeAction(key);
      return;
    }
    this.completeAction(key);
  }

  exportCsv(): void {
    if (!this.hasRows) {
      return;
    }
    const headers = this.data.columns.map(col => col.label);
    const csvRows = this.data.rows.map(row => this.data.columns.map(col => this.escapeCsv(this.formatCell(row[col.key]))));
    const csv = [headers, ...csvRows].map(cols => cols.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const timestamp = new Date().toISOString().replace(/[:T]/g, '-').split('.')[0];
    const link = document.createElement('a');
    link.href = url;
    link.download = `${this.slugify(this.data.title)}-${timestamp}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  }

  close(): void {
    this.dialogRef.close();
  }

  private escapeCsv(value: string): string {
    const needsQuotes = value.includes(',') || value.includes('"') || value.includes('\n');
    const escaped = value.replace(/"/g, '""');
    return needsQuotes ? `"${escaped}"` : escaped;
  }

  private slugify(input: string): string {
    return (input || 'export')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .substring(0, 60) || 'export';
  }

  private isPromise(value: unknown): value is Promise<unknown> {
    return !!value && typeof (value as Promise<unknown>).then === 'function';
  }

  private completeAction(key: string): void {
    this.executing.delete(key);
    this.cdr.markForCheck();
  }
}
