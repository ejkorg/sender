import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatTableModule } from '@angular/material/table';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
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
  imports: [CommonModule, FormsModule, MatTableModule, MatCheckboxModule, MatButtonModule],
  template: `
  <h2>User Role Management</h2>
  <table mat-table [dataSource]="users" class="mat-elevation-z2" style="width:100%;margin-top:16px;">
    <ng-container matColumnDef="username">
      <th mat-header-cell *matHeaderCellDef>Username</th>
      <td mat-cell *matCellDef="let user">{{user.username}}</td>
    </ng-container>
    <ng-container matColumnDef="email">
      <th mat-header-cell *matHeaderCellDef>Email</th>
      <td mat-cell *matCellDef="let user">{{user.email}}</td>
    </ng-container>
    <ng-container matColumnDef="enabled">
      <th mat-header-cell *matHeaderCellDef>Enabled</th>
      <td mat-cell *matCellDef="let user">{{user.enabled ? 'Yes' : 'No'}}</td>
    </ng-container>
    <ng-container matColumnDef="role_user">
      <th mat-header-cell *matHeaderCellDef>User</th>
      <td mat-cell *matCellDef="let user">
        <mat-checkbox [(ngModel)]="user.rolesMap.user"></mat-checkbox>
      </td>
    </ng-container>
    <ng-container matColumnDef="role_admin">
      <th mat-header-cell *matHeaderCellDef>Admin</th>
      <td mat-cell *matCellDef="let user">
        <mat-checkbox [(ngModel)]="user.rolesMap.admin"></mat-checkbox>
      </td>
    </ng-container>
    <ng-container matColumnDef="actions">
      <th mat-header-cell *matHeaderCellDef>Actions</th>
      <td mat-cell *matCellDef="let user">
        <button mat-button (click)="saveRoles(user)">Save</button>
        <span *ngIf="user.saveStatus === 'success'" style="color:green;margin-left:8px;">✔</span>
        <span *ngIf="user.saveStatus === 'error'" style="color:red;margin-left:8px;">✖</span>
      </td>
    </ng-container>
    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
    <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
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
