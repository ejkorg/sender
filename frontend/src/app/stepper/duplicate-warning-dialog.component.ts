import { CommonModule, formatDate } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IconComponent } from '../ui/icon.component';
import { TooltipDirective } from '../ui/tooltip.directive';
import { DuplicatePayloadInfo } from '../api/backend.service';

export interface DuplicateWarningDialogData {
  currentUser: string | null;
  duplicates: DuplicatePayloadInfo[];
}

@Component({
  selector: 'app-duplicate-warning-dialog',
  standalone: true,
  imports: [CommonModule, IconComponent, TooltipDirective],
  templateUrl: './duplicate-warning-dialog.component.html',
  styleUrls: ['./duplicate-warning-dialog.component.css']
})
export class DuplicateWarningDialogComponent {
  @Input() data!: DuplicateWarningDialogData;
  @Output() close = new EventEmitter<boolean | null>();

  readonly uiDateFormat = 'yyyy-MM-dd HH:mm:ss';

  get duplicatesToShow(): DuplicatePayloadInfo[] {
    return (this.data?.duplicates || []).slice(0, 20);
  }

  get otherUserCount(): number {
    return (this.data?.duplicates || []).filter(dup => this.isDifferentUser(dup)).length;
  }

  continue(): void {
    this.close.emit(true);
  }

  cancel(): void {
    this.close.emit(false);
  }

  displayUser(user: string | null | undefined): string {
    if (!user) {
      return 'unknown';
    }
    const trimmed = user.trim();
    return trimmed.length ? trimmed : 'unknown';
  }

  formatTimestamp(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    try {
      const date = new Date(value);
      if (isNaN(date.getTime())) {
        return value;
      }
      return formatDate(date, this.uiDateFormat, 'en-US');
    } catch (err) {
      return value;
    }
  }

  private isDifferentUser(dup: DuplicatePayloadInfo): boolean {
    if (!dup) {
      return false;
    }
    const previous = this.displayUser(dup.lastRequestedBy ?? dup.stagedBy);
    const current = this.displayUser(this.data?.currentUser ?? '');
    return previous.toLowerCase() !== current.toLowerCase();
  }
}
