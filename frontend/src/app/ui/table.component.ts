import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-table',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="overflow-auto">
      <table class="min-w-full bg-white" [ngClass]="tableClass">
        <ng-content></ng-content>
      </table>
    </div>
  `
})
export class TableComponent {
  @Input() tableClass = '';
}
