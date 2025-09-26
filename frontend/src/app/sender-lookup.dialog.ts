import { Component, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-sender-lookup-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>Select Sender</h2>
    <div *ngFor="let row of data" style="padding:8px; display:flex; justify-content:space-between; align-items:center;">
      <div>{{row.name}}</div>
      <button (click)="select(row)">Select</button>
    </div>
    <div style="margin-top:12px; text-align:right;"><button (click)="close()">Close</button></div>
  `
})
export class SenderLookupDialogComponent {
  constructor(public dialogRef: MatDialogRef<SenderLookupDialogComponent>, @Inject(MAT_DIALOG_DATA) public data: any) {}

  select(item: any) {
    this.dialogRef.close(item);
  }

  close() { this.dialogRef.close(null); }
}
