import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ButtonComponent } from './ui/button.component';
import { FormsModule } from '@angular/forms';

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
  <h2>User Role Management</h2>
  <table style="width:100%;margin-top:16px;border-collapse:collapse;">
    <thead>
      <tr>
        <th style="text-align:left;padding:8px;border-bottom:1px solid #e5e7eb;">Username</th>
        <th style="text-align:left;padding:8px;border-bottom:1px solid #e5e7eb;">Email</th>
        <th style="text-align:left;padding:8px;border-bottom:1px solid #e5e7eb;">Enabled</th>
        <th style="text-align:left;padding:8px;border-bottom:1px solid #e5e7eb;">User</th>
        <th style="text-align:left;padding:8px;border-bottom:1px solid #e5e7eb;">Admin</th>
        <th style="text-align:left;padding:8px;border-bottom:1px solid #e5e7eb;">Actions</th>
      </tr>
    </thead>
    <tbody>
      <tr *ngFor="let user of users">
        <td style="padding:8px;border-bottom:1px solid #f3f4f6;">{{ user.username }}</td>
        <td style="padding:8px;border-bottom:1px solid #f3f4f6;">{{ user.email }}</td>
        <td style="padding:8px;border-bottom:1px solid #f3f4f6;">{{ user.enabled ? 'Yes' : 'No' }}</td>
        <td style="padding:8px;border-bottom:1px solid #f3f4f6;"><input type="checkbox" [(ngModel)]="user.rolesMap.user" /></td>
        <td style="padding:8px;border-bottom:1px solid #f3f4f6;"><input type="checkbox" [(ngModel)]="user.rolesMap.admin" /></td>
        <td style="padding:8px;border-bottom:1px solid #f3f4f6;">
          <app-button variant="primary" (click)="saveRoles(user)">Save</app-button>
          <span *ngIf="user.saveStatus === 'success'" style="color:green;margin-left:8px;">✔</span>
          <span *ngIf="user.saveStatus === 'error'" style="color:red;margin-left:8px;">✖</span>
        </td>
      </tr>
    </tbody>
  </table>
  `
})
export class AdminDashboardComponent implements OnInit {
  users: any[] = [];
  displayedColumns = ['username', 'email', 'enabled', 'role_user', 'role_admin', 'actions'];

  constructor(private http: HttpClient, private snack: MatSnackBar) {}

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
}
