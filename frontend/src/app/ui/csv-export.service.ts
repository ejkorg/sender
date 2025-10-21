import { Injectable } from '@angular/core';

export interface CsvExportOptions {
  filename?: string;
  headers?: Array<string>;
  rows: Array<Array<string | number | boolean | Date | null | undefined>>;
  includeBom?: boolean;
  includeHeaders?: boolean;
  addTimestamp?: boolean;
}

@Injectable({ providedIn: 'root' })
export class CsvExportService {
  download(options: CsvExportOptions): string | null {
    const {
      filename = 'export',
      headers = [],
      rows,
      includeBom = false,
      includeHeaders = true,
      addTimestamp = true
    } = options;

    if ((!rows || rows.length === 0) && (!includeHeaders || headers.length === 0)) {
      return null;
    }

    const table: string[][] = [];
    if (includeHeaders && headers.length) {
      table.push(headers.map(header => this.escapeCell(header)));
    }

    for (const row of rows || []) {
      const serialized = (row || []).map(cell => this.escapeCell(this.serializeCell(cell)));
      table.push(serialized);
    }

    if (!table.length) {
      return null;
    }

    const csv = table.map(columns => columns.join(',')).join('\n');
    const payload = includeBom ? ['\ufeff', csv] : [csv];
    const blob = new Blob(payload, { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const timestamp = addTimestamp ? `-${this.timestamp()}` : '';
    const safeName = `${this.slugify(filename)}${timestamp}.csv`;

    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = safeName;
    anchor.click();
    window.URL.revokeObjectURL(url);

    return safeName;
  }

  private serializeCell(value: string | number | boolean | Date | null | undefined): string {
    if (value === null || value === undefined) {
      return '';
    }
    if (value instanceof Date) {
      if (isNaN(value.getTime())) {
        return '';
      }
      return value.toISOString();
    }
    if (typeof value === 'boolean') {
      return value ? 'true' : 'false';
    }
    if (typeof value === 'number') {
      if (!Number.isFinite(value)) {
        return '';
      }
      return String(value);
    }
    return String(value ?? '');
  }

  private escapeCell(value: string): string {
    if (value.includes('"') || value.includes(',') || value.includes('\n')) {
      return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
  }

  private slugify(raw: string): string {
    return (raw || 'export')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .substring(0, 80) || 'export';
  }

  private timestamp(): string {
    const now = new Date();
    const pad = (value: number) => value.toString().padStart(2, '0');
    return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  }
}
