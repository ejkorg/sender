import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SenderOption } from '../api/backend.service';

@Component({
  selector: 'app-sender-selector',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sender-selector.component.html'
})
export class SenderSelectorComponent {
  @Input() senderOptions: SenderOption[] = [];
  @Input() selectedSenderId: number | null = null;
  @Input() selectedSenderName: string | null = null;
  @Input() senderFallback = false;
  @Input() senderLookupLoading = false;
  @Input() senderLookupLog: any = null;
  @Input() senderAutoResolved = false;

  @Output() selectSender = new EventEmitter<number | null>();
  @Output() senderChanged = new EventEmitter<number | null>();

  // local helpers can be added here if necessary
}
