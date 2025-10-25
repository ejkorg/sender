import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'truncate',
  standalone: true
})
export class TruncatePipe implements PipeTransform {
  transform(value: string | number | null | undefined, length: number = 36): string {
    if (value === null || value === undefined) return '—';
    const s = String(value);
    if (!s.length) return '—';
    if (!length || length <= 0) return s;
    return s.length > length ? s.slice(0, length) + '…' : s;
  }
}
