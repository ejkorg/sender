import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DuplicatePayloadInfo } from '../api/backend.service';

export interface DuplicateWarningDialogData {
  currentUser: string | null;
  duplicates: DuplicatePayloadInfo[];
}

@Component({
  selector: 'app-duplicate-warning-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, MatListModule, MatTooltipModule],
  templateUrl: './duplicate-warning-dialog.component.html',
  styleUrls: ['./duplicate-warning-dialog.component.css']
})
export class DuplicateWarningDialogComponent {
  readonly duplicatesToShow: DuplicatePayloadInfo[];

  constructor(@Inject(MAT_DIALOG_DATA) public data: DuplicateWarningDialogData,
              private dialogRef: MatDialogRef<DuplicateWarningDialogComponent>) {
    this.duplicatesToShow = (data.duplicates || []).slice(0, 20);
  }

  get otherUserCount(): number {
    return (this.data.duplicates || []).filter(dup => this.isDifferentUser(dup)).length;
  }

  continue(): void {
    this.dialogRef.close(true);
  }

  cancel(): void {
    this.dialogRef.close(false);
  }

  displayUser(user: string | null | undefined): string {
    if (!user) {
      return 'unknown';
    }
    const trimmed = user.trim();
    return trimmed.length ? trimmed : 'unknown';
  }

  formatDate(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    try {
      const date = new Date(value);
      if (isNaN(date.getTime())) {
        return value;
      }
      return new Intl.DateTimeFormat(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      }).format(date);
    } catch (err) {
      return value;
    }
  }

  private isDifferentUser(dup: DuplicatePayloadInfo): boolean {
    if (!dup) {
      return false;
    }
    const previous = this.displayUser(dup.lastRequestedBy ?? dup.stagedBy);
    const current = this.displayUser(this.data.currentUser ?? '');
    return previous.toLowerCase() !== current.toLowerCase();
  }
}
