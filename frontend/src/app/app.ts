import { Component, OnInit } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { SenderLookupDialogComponent } from './sender-lookup.dialog';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatToolbarModule } from '@angular/material/toolbar';
import { HttpClientModule } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { LoginComponent } from './auth/login.component';

@Component({
  selector: 'app-root',
  standalone: true,
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
    MatDialogModule,
    LoginComponent
  ],
  templateUrl: './app.html',
  styleUrls: ['./app.css']
})
export class App implements OnInit {

  sites: string[] = [];
  selectedSite: string = '';
  environments: Array<{name:string,description?:string}> = [];
  selectedEnvironment: string = '';
  locations: Array<{id:number,label:string,dbConnectionName:string,site?:string}> = [];
  // values pulled from the selected external DB connection
  externalLocations: string[] = [];
  externalLocationValue: string = '';
  dataTypes: string[] = [];
  testerTypes: string[] = [];
  testPhases: string[] = [];
  loadingExternalLocations: boolean = false;
  loadingDataTypes: boolean = false;
  loadingTesterTypes: boolean = false;
  loadingTestPhases: boolean = false;
  selectedLocationId: number | null = null;
  metadataLocation: string = '';
  dataType: string = '';
  testerType: string = '';
  testPhase: string = '';
  matchNoTestPhase: boolean = false;
  senderId: number | null = null;
  numToSend: number = 300;
  result: string = '';
  loading: boolean = false;
  lookupResults: Array<{idSender:number|null,name:string}> = [];
  lookupDialogRef: any = null;

  constructor(private http: HttpClient, public auth: AuthService, private snack: MatSnackBar, private dialog: MatDialog) {}

  private locationSelect$ = new Subject<number | null>();
  private locationSelectSub: Subscription | null = null;

  ngOnInit() {
    this.loadSites();
    this.loadEnvironments();
    // debounce saved connection selection to avoid rapid repeated external queries
    this.locationSelectSub = this.locationSelect$.pipe(debounceTime(300)).subscribe(id => {
      if (id) this.fetchExternalLocations();
      else this.externalLocations = [];
    });
  }

  ngOnDestroy() {
    if (this.locationSelectSub) this.locationSelectSub.unsubscribe();
  }

  loadSites() {
    this.http.get<string[]>('http://localhost:8080/api/sites').subscribe(
      data => this.sites = data,
      error => console.error('Error loading sites', error)
    );
  }

  loadEnvironments() {
    this.http.get<any[]>('http://localhost:8080/api/environments').subscribe(
      data => this.environments = data,
      error => console.error('Error loading environments', error)
    );
  }

  loadLocationsForEnvironment(env: string) {
    if (!env) { this.locations = []; this.selectedLocationId = null; return; }
    this.http.get<any[]>(`http://localhost:8080/api/environments/${encodeURIComponent(env)}/locations`).subscribe(
      data => this.locations = data,
      error => console.error('Error loading locations', error)
    );
  }

  onLocationSelected() {
    if (!this.selectedLocationId) return;
    const sel = this.locations.find(l => l.id === this.selectedLocationId);
    if (!sel) return;
    // Auto-fill site from the saved ExternalLocation if present
    if (sel.site) this.selectedSite = sel.site;
    // Optionally auto-fill metadataLocation from label when metadataLocation is empty
    if (!this.metadataLocation && sel.label) {
      this.metadataLocation = sel.label;
    }
    // Debounced fetch to avoid rapid repeated queries when user is changing selection
    this.locationSelect$.next(this.selectedLocationId);
  }

  fetchExternalLocations() {
    if (!this.selectedLocationId) { this.externalLocations = []; return; }
    const params: any = { locationId: String(this.selectedLocationId), environment: this.selectedEnvironment || 'qa' };
    this.loadingExternalLocations = true;
    this.http.get<string[]>('http://localhost:8080/api/senders/external/locations', { params }).subscribe(
      data => { this.externalLocations = data; this.loadingExternalLocations = false; },
      err => { console.error('Failed loading external locations', err); this.externalLocations = []; this.loadingExternalLocations = false; }
    );
  }

  onExternalLocationSelected() {
    // when user selects a value from externalLocations, fetch the dependent dropdowns
    if (!this.externalLocationValue) return;
    // fetch data types, tester types and test phases in parallel
    this.fetchDataTypes();
    this.fetchTesterTypes();
    this.fetchTestPhases();
    // also run lookup to auto-select sender if unique
    this.autoLookupSenderForExternalSelection();
  }

  fetchDataTypes() {
    if (!this.selectedLocationId || !this.externalLocationValue) { this.dataTypes = []; return; }
    const params: any = { locationId: String(this.selectedLocationId), location: this.externalLocationValue, environment: this.selectedEnvironment || 'qa' };
    this.loadingDataTypes = true;
    this.http.get<string[]>('http://localhost:8080/api/senders/external/dataTypes', { params }).subscribe(
      data => { this.dataTypes = data; this.loadingDataTypes = false; },
      err => { console.error('Failed loading data types', err); this.dataTypes = []; this.loadingDataTypes = false; }
    );
  }

  fetchTesterTypes() {
    if (!this.selectedLocationId || !this.externalLocationValue) { this.testerTypes = []; return; }
    const params: any = { locationId: String(this.selectedLocationId), location: this.externalLocationValue, environment: this.selectedEnvironment || 'qa' };
    this.loadingTesterTypes = true;
    this.http.get<string[]>('http://localhost:8080/api/senders/external/testerTypes', { params }).subscribe(
      data => { this.testerTypes = data; this.loadingTesterTypes = false; },
      err => { console.error('Failed loading tester types', err); this.testerTypes = []; this.loadingTesterTypes = false; }
    );
  }

  fetchTestPhases() {
    if (!this.selectedLocationId || !this.externalLocationValue) { this.testPhases = []; return; }
    const params: any = { locationId: String(this.selectedLocationId), location: this.externalLocationValue, environment: this.selectedEnvironment || 'qa' };
    this.loadingTestPhases = true;
    this.http.get<string[]>('http://localhost:8080/api/senders/external/testPhases', { params }).subscribe(
      data => { this.testPhases = data; this.loadingTestPhases = false; },
      err => { console.error('Failed loading test phases', err); this.testPhases = []; this.loadingTestPhases = false; }
    );
  }

  autoLookupSenderForExternalSelection() {
    if (!this.selectedLocationId || !this.externalLocationValue) return;
    const params: any = { locationId: String(this.selectedLocationId), metadataLocation: this.externalLocationValue, environment: this.selectedEnvironment || 'qa' };
    if (this.dataType) params.dataType = this.dataType;
    if (this.testerType) params.testerType = this.testerType;
    if (this.matchNoTestPhase) params.testPhase = '';
    else if (this.testPhase) params.testPhase = this.testPhase;
    this.http.get<any[]>('http://localhost:8080/api/senders/lookup', { params }).subscribe(
      data => {
        if (!data) return;
        if (data.length === 1 && data[0].idSender) {
          // Close dialog if open and set id
          if (this.lookupDialogRef) {
            this.lookupDialogRef.close(data[0]);
            this.lookupDialogRef = null;
          }
          this.senderId = data[0].idSender;
          this.snack.open('Sender auto-filled', 'OK', { duration: 2000 });
        } else if (data.length > 1) {
          // if multiple results and dialog not already open, open it
          if (!this.lookupDialogRef) {
            this.lookupDialogRef = this.dialog.open(SenderLookupDialogComponent, { data: data.map(d => ({ idSender: d.idSender, name: d.name })), width: '600px' });
            this.lookupDialogRef.afterClosed().subscribe((sel: any) => {
              this.lookupDialogRef = null;
              if (sel && sel.idSender) {
                this.senderId = sel.idSender;
                this.snack.open('Sender selected', 'OK', { duration: 2000 });
              }
            });
          }
        }
      }, err => { console.error('Auto lookup failed', err); }
    );
  }

  findSenders() {
    if (!this.selectedLocationId) { this.snack.open('Select DB Location first', 'Close', { duration: 3000 }); return; }
    const params: any = {
      site: this.selectedSite,
      environment: this.selectedEnvironment || 'qa'
    };
    if (this.metadataLocation) params.metadataLocation = this.metadataLocation;
    if (this.dataType) params.dataType = this.dataType;
    if (this.testerType) params.testerType = this.testerType;
    if (this.matchNoTestPhase) {
      // send empty string to indicate match of NULL test_phase (backend treats blank as NULL match)
      params.testPhase = '';
    } else if (this.testPhase) {
      params.testPhase = this.testPhase;
    }
    params.locationId = String(this.selectedLocationId);
    this.loading = true;
    this.http.get<any[]>('http://localhost:8080/api/senders/lookup', { params }).subscribe(
      data => {
        this.lookupResults = data.map(d => ({ idSender: d.idSender, name: d.name }));
        this.loading = false;
        if (this.lookupResults.length === 1 && this.lookupResults[0].idSender) {
          this.senderId = this.lookupResults[0].idSender;
          this.snack.open('Sender auto-filled', 'OK', { duration: 2000 });
        } else if (this.lookupResults.length === 0) {
          this.snack.open('No matching senders found', 'Close', { duration: 4000 });
        } else {
          // open dialog for selection
          this.lookupDialogRef = this.dialog.open(SenderLookupDialogComponent, { data: this.lookupResults, width: '600px' });
          this.lookupDialogRef.afterClosed().subscribe((sel: any) => {
            this.lookupDialogRef = null;
            if (sel && sel.idSender) {
              this.senderId = sel.idSender;
              this.snack.open('Sender selected', 'OK', { duration: 2000 });
            }
          });
        }
      },
      err => {
        this.loading = false;
        this.snack.open('Lookup failed: ' + (err.message || err.statusText), 'Close', { duration: 5000 });
      }
    );
  }

  runNow() {
    // Call the updated discover endpoint which accepts metadataLocation and locationId
    const params: any = {
      site: this.selectedSite,
      environment: this.selectedEnvironment || 'qa',
      writeListFile: true,
      numberOfDataToSend: String(this.numToSend),
      countLimitTrigger: String(600)
    };
    if (this.metadataLocation) params.metadataLocation = this.metadataLocation;
    if (this.selectedLocationId) params.locationId = String(this.selectedLocationId);

    if (!this.senderId) {
      this.snack.open('Sender ID is required', 'Close', { duration: 4000 });
      return;
    }
    const url = `http://localhost:8080/api/senders/${this.senderId}/discover`;
    this.loading = true;
    this.http.post(url, null, { params: params, responseType: 'text' }).subscribe(
      data => {
        this.result = data as string;
        this.loading = false;
        this.snack.open('Discovery completed', 'OK', { duration: 3000 });
      },
      error => {
        this.loading = false;
        const msg = 'Error: ' + (error.message || error.statusText);
        this.result = msg;
        this.snack.open('Discovery failed: ' + (error.statusText || 'network'), 'Close', { duration: 6000 });
      }
    );
  }
}
