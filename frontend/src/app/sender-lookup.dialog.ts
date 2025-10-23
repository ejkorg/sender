import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-sender-lookup',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="rounded-2xl bg-surface p-4 shadow-md">
      <h2 class="text-lg font-semibold mb-3">Select Sender</h2>
      <div *ngIf="items?.length; else empty">
        <div *ngFor="let row of items" class="flex items-center justify-between p-2 border-b last:border-b-0">
          <div class="text-sm">{{ row.name }}</div>
          <button class="btn-primary text-sm px-3 py-1" (click)="onSelect(row)">Select</button>
        </div>
      </div>
      <ng-template #empty>
        <div class="text-sm text-slate-600">No senders found.</div>
      </ng-template>
      <div class="mt-4 text-right">
        <button class="btn-secondary px-3 py-1" (click)="onClose()">Close</button>
      </div>
    </div>
  `
})
export class SenderLookupDialogComponent {
  @Input() items: Array<{ idSender?: number; name?: string }> | null = [];
  @Output() select = new EventEmitter<any | null>();
  @Output() closed = new EventEmitter<void>();

  onSelect(item: any) {
    this.select.emit(item);
  }

  onClose() {
    this.closed.emit();
  }
}
