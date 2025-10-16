import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface DashboardDetailColumn {
  key: string;
  label: string;
  align?: 'start' | 'end' | 'center';
}

export interface DashboardDetailDialogData {
  title: string;
  description?: string;
  columns: DashboardDetailColumn[];
  rows: Array<Record<string, string | number | null | undefined>>;
}

@Component({
  selector: 'app-dashboard-detail-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './dashboard-detail-dialog.component.html',
  styleUrls: ['./dashboard-detail-dialog.component.css']
})
export class DashboardDetailDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: DashboardDetailDialogData,
    private dialogRef: MatDialogRef<DashboardDetailDialogComponent>
  ) {}

  get hasRows(): boolean {
    return (this.data?.rows?.length ?? 0) > 0;
  }

  formatCell(value: string | number | null | undefined): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return String(value);
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
}
