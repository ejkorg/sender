import { Component, OnInit } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { FormsModule } from '@angular/forms';
import { BackendService, ReloadRequest, StageStatus } from './api/backend.service';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { LoginComponent } from './auth/login.component';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './auth/auth.interceptor';

@Component({
  selector: 'app-root',
  standalone: true,
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
  ],
  imports: [
    FormsModule,
    CommonModule,
    MatSnackBarModule,
    MatToolbarModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    LoginComponent
  ],
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class App implements OnInit {
  sites: string[] = [];
  selectedSite = '';
  senderId: number | null = null;
  startDate = '';
  endDate = '';
  testerType = '';
  dataType = '';
  result = '';
  loading = false;
  stageLoading = false;
  stageStatuses: StageStatus[] = [];

  constructor(private backend: BackendService, public auth: AuthService, private snack: MatSnackBar) {}

  ngOnInit() {
    this.refreshSites();
    this.refreshStageStatus();
  }

  refreshSites() {
    this.backend.listSites().subscribe({
      next: sites => {
        this.sites = sites || [];
        if (!this.selectedSite && this.sites.length) {
          this.selectedSite = this.sites[0];
        }
      },
      error: err => console.error('Failed loading sites', err)
    });
  }

  refreshStageStatus() {
    this.stageLoading = true;
    this.backend.getStageStatus().subscribe({
      next: statuses => {
        this.stageStatuses = (statuses || []).sort((a, b) => a.site.localeCompare(b.site));
        this.stageLoading = false;
      },
      error: err => {
        console.error('Failed loading stage status', err);
        this.stageStatuses = [];
        this.stageLoading = false;
      }
    });
  }

  runNow() {
    if (!this.selectedSite) {
      this.snack.open('Select a site', 'Close', { duration: 3000 });
      return;
    }
    if (!this.senderId || this.senderId <= 0) {
      this.snack.open('Enter a valid sender ID', 'Close', { duration: 3000 });
      return;
    }

    const payload: ReloadRequest = {
      site: this.selectedSite,
      senderId: String(this.senderId)
    };
    if (this.startDate) payload.startDate = this.startDate;
    if (this.endDate) payload.endDate = this.endDate;
    if (this.testerType) payload.testerType = this.testerType;
    if (this.dataType) payload.dataType = this.dataType;

    this.loading = true;
    this.backend.reload(payload).subscribe({
      next: message => {
        this.result = message;
        this.loading = false;
        this.snack.open('Reload request accepted', 'OK', { duration: 3000 });
        this.refreshStageStatus();
      },
      error: err => {
        const message = err?.error || err?.message || 'Request failed';
        this.result = typeof message === 'string' ? message : 'Reload failed';
        this.loading = false;
        this.snack.open('Reload failed', 'Close', { duration: 4000 });
      }
    });
  }
}
