import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackendService } from './api/backend.service';

@Component({
  selector: 'app-discovery',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './discovery.component.html'
})
export class DiscoveryComponent implements OnInit {
  environments: any[] = [];
  locations: any[] = [];
  selectedEnv = 'qa';
  selectedLocationId: number | null = null;
  metadataLocation = '';
  result = '';

  constructor(private api: BackendService) {}

  ngOnInit(){
    this.api.listEnvironments().subscribe(e => this.environments = e);
  }

  loadLocations(){
    if (!this.selectedEnv) { this.locations = []; return; }
    this.api.listLocations(this.selectedEnv).subscribe(l => this.locations = l);
  }

  runDiscovery(senderId: number){
    const params: any = { environment: this.selectedEnv };
    if (this.selectedLocationId) params.locationId = this.selectedLocationId;
    if (this.metadataLocation) params.metadataLocation = this.metadataLocation;
    this.api.discover(senderId, params).subscribe(resp => this.result = String(resp), err => this.result = 'Error: ' + (err.message || err.statusText));
  }
}
