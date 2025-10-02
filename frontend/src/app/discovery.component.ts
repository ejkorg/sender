import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackendService } from './api/backend.service';
import { Subject, Subscription, merge } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-discovery',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './discovery.component.html'
})
export class DiscoveryComponent implements OnInit {
  environments: any[] = [];
  instances: {key:string,label:string,environment:string}[] = [];
  locations: any[] = [];
  // distinct lists pulled from the selected external instance (connectionKey)
  distinctLocations: string[] = [];
  distinctDataTypes: string[] = [];
  distinctTesterTypes: string[] = [];
  // loading flags
  loadingDistinctLocations = false;
  loadingDistinctDataTypes = false;
  loadingDistinctTesterTypes = false;
  // reactive subjects to observe filter changes
  private instanceChange$ = new Subject<void>();
  private filtersChange$ = new Subject<void>();
  private subs: Subscription | null = null;
  // Note: backend currently exposes distinct endpoints for locations, dataTypes and testerTypes.
  // There's no distinct testPhase endpoint in the frontend service, so testPhase remains a free-text filter.
  selectedEnv = 'qa';
  selectedInstanceKey: string | null = null;
  selectedLocationId: number | null = null;
  metadataLocation = '';
  dataType = '';
  testerType = '';
  testPhase = '';
  result = '';

  constructor(private api: BackendService) {}

  ngOnInit(){
    this.api.listEnvironments().subscribe(e => this.environments = e);
    this.loadInstances();
    // subscribe to combined change events and load distincts with a small debounce
    this.subs = merge(this.instanceChange$, this.filtersChange$)
      .pipe(debounceTime(250))
      .subscribe(() => this.loadDistincts());
  }

  loadInstances(){
    if (!this.selectedEnv) { this.instances = []; return; }
    this.api.listInstances(this.selectedEnv).subscribe(inst => {
      this.instances = inst;
      // clear any previously selected instance when environment changes
      this.selectedInstanceKey = null;
      this.clearDistincts();
    });
  }

  loadLocations(){
    if (!this.selectedEnv) { this.locations = []; return; }
    this.api.listLocations(this.selectedEnv).subscribe(l => this.locations = l);
  }

  runDiscovery(senderId: number){
    const params: any = { environment: this.selectedEnv };
    if (this.selectedInstanceKey) params.site = this.selectedInstanceKey;
    if (this.selectedLocationId) params.locationId = this.selectedLocationId;
    if (this.metadataLocation) params.metadataLocation = this.metadataLocation;
    this.api.discover(senderId, params).subscribe(resp => this.result = String(resp), err => this.result = 'Error: ' + (err.message || err.statusText));
  }

  // Optional helper lookups using the selected instanceKey instead of a saved locationId
  onInstanceChange(){
    // when the user selects a different instance, reload the distinct lists
    this.instanceChange$.next();
    // also refresh saved locations for the environment
    this.loadLocations();
  }

  // called when filter inputs change (dataType/testerType/testPhase/metadataLocation)
  onFilterChange(){
    this.filtersChange$.next();
  }

  loadDistincts(){
    if (!this.selectedInstanceKey) { this.clearDistincts(); return; }

    // locations
    this.loadingDistinctLocations = true;
    this.api.getDistinctLocations({ connectionKey: this.selectedInstanceKey, environment: this.selectedEnv, dataType: this.dataType, testerType: this.testerType, testPhase: this.testPhase })
      .subscribe(x => { this.distinctLocations = x || []; this.loadingDistinctLocations = false; }, _ => { this.distinctLocations = []; this.loadingDistinctLocations = false; });

    // data types
    this.loadingDistinctDataTypes = true;
    this.api.getDistinctDataTypes({ connectionKey: this.selectedInstanceKey, environment: this.selectedEnv, location: this.metadataLocation, testerType: this.testerType, testPhase: this.testPhase })
      .subscribe(x => { this.distinctDataTypes = x || []; this.loadingDistinctDataTypes = false; }, _ => { this.distinctDataTypes = []; this.loadingDistinctDataTypes = false; });

    // tester types
    this.loadingDistinctTesterTypes = true;
    this.api.getDistinctTesterTypes({ connectionKey: this.selectedInstanceKey, environment: this.selectedEnv, location: this.metadataLocation, dataType: this.dataType, testPhase: this.testPhase })
      .subscribe(x => { this.distinctTesterTypes = x || []; this.loadingDistinctTesterTypes = false; }, _ => { this.distinctTesterTypes = []; this.loadingDistinctTesterTypes = false; });
  }

  clearDistincts(){
    this.distinctLocations = [];
    this.distinctDataTypes = [];
    this.distinctTesterTypes = [];
  }
  ngOnDestroy(): void {
    this.subs?.unsubscribe();
  }
}
