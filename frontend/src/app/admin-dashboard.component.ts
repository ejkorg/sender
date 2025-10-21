import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ToastService } from './ui/toast.service';
import { ButtonComponent } from './ui/button.component';
import { FormsModule } from '@angular/forms';
import { CsvExportService } from './ui/csv-export.service';

interface UserSummary {
  id: number;
  username: string;
  email: string;
  roles: string[];
  enabled: boolean;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonComponent],
  template: `
  <div class="flex flex-col gap-4">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <h2 class="text-xl font-semibold text-onsemi-charcoal">User Role Management</h2>
      <app-button variant="secondary" (click)="exportUsersCsv()" [disabled]="!users.length">Export CSV</app-button>
    </div>
    <div class="overflow-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
      <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-onsemi-ice text-onsemi-charcoal">
          <tr>
            <th class="px-4 py-3 text-left font-semibold">Username</th>
            <th class="px-4 py-3 text-left font-semibold">Email</th>
            <th class="px-4 py-3 text-left font-semibold">Enabled</th>
            <th class="px-4 py-3 text-left font-semibold">User</th>
            <th class="px-4 py-3 text-left font-semibold">Admin</th>
            <th class="px-4 py-3 text-left font-semibold">Actions</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <tr *ngFor="let user of users" class="bg-white">
            <td class="px-4 py-3 font-medium text-onsemi-charcoal">{{ user.username }}</td>
            <td class="px-4 py-3 text-slate-700">{{ user.email }}</td>
            <td class="px-4 py-3 text-slate-700">{{ user.enabled ? 'Yes' : 'No' }}</td>
            <td class="px-4 py-3 text-slate-700"><input type="checkbox" class="h-4 w-4 rounded border-slate-300 text-onsemi-primary focus:ring-onsemi-primary/40" [(ngModel)]="user.rolesMap.user" /></td>
            <td class="px-4 py-3 text-slate-700"><input type="checkbox" class="h-4 w-4 rounded border-slate-300 text-onsemi-primary focus:ring-onsemi-primary/40" [(ngModel)]="user.rolesMap.admin" /></td>
            <td class="px-4 py-3 text-slate-700">
              <div class="flex items-center gap-2">
                <app-button variant="primary" (click)="saveRoles(user)">Save</app-button>
                <span *ngIf="user.saveStatus === 'success'" class="text-green-600">✔</span>
                <span *ngIf="user.saveStatus === 'error'" class="text-red-500">✖</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="px-4 py-3 text-sm text-slate-500" *ngIf="!users.length">No users available.</p>
    </div>
  </div>
  `
})
export class AdminDashboardComponent implements OnInit {
  users: any[] = [];
  displayedColumns = ['username', 'email', 'enabled', 'role_user', 'role_admin', 'actions'];

  constructor(private http: HttpClient, private toast: ToastService, private csv: CsvExportService) {}

  ngOnInit() {
    this.loadUsers();
  }

  loadUsers() {
    this.http.get<UserSummary[]>('/api/admin/users').subscribe(users => {
      this.users = users.map(u => ({
        ...u,
        rolesMap: {
          user: u.roles.includes('ROLE_USER'),
          admin: u.roles.includes('ROLE_ADMIN')
        }
      }));
    });
  }

  saveRoles(user: any) {
    const roles = [];
    if (user.rolesMap.user) roles.push('ROLE_USER');
    if (user.rolesMap.admin) roles.push('ROLE_ADMIN');
    user.saveStatus = 'pending';
    this.http.post(`/api/admin/users/${user.id}/roles`, { roles }).subscribe({
      next: () => {
        user.saveStatus = 'success';
        setTimeout(() => user.saveStatus = undefined, 2000);
      },
      error: () => {
        user.saveStatus = 'error';
        setTimeout(() => user.saveStatus = undefined, 3000);
      }
    });
  }

  exportUsersCsv(): void {
    if (!this.users.length) {
      this.toast.info('No users to export.');
      return;
    }

    const rows = this.users.map(user => [
      user.username ?? '',
      user.email ?? '',
      user.enabled ? 'Yes' : 'No',
      user.rolesMap?.user ? 'Yes' : 'No',
      user.rolesMap?.admin ? 'Yes' : 'No',
      Array.isArray(user.roles) ? user.roles.join(' ') : ''
    ]);

    const exported = this.csv.download({
      filename: 'user-roles',
      headers: ['Username', 'Email', 'Enabled', 'Role: User', 'Role: Admin', 'Raw Roles'],
      rows
    });

    if (exported) {
      this.toast.success(`Exported ${rows.length} user${rows.length === 1 ? '' : 's'} to CSV.`);
    } else {
      this.toast.error('Unable to create user export.');
    }
  }
}
