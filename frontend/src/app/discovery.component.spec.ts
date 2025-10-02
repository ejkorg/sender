import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { DiscoveryComponent } from './discovery.component';
import { BackendService } from './api/backend.service';
import { of } from 'rxjs';

describe('DiscoveryComponent', () => {
  let component: DiscoveryComponent;
  let fixture: ComponentFixture<DiscoveryComponent>;
  let mockApi: jasmine.SpyObj<BackendService>;

  beforeEach(waitForAsync(() => {
    mockApi = jasmine.createSpyObj('BackendService', ['listEnvironments','listInstances','listLocations','getDistinctLocations','getDistinctDataTypes','getDistinctTesterTypes','discover']);
    mockApi.listEnvironments.and.returnValue(of([]));
    mockApi.listInstances.and.returnValue(of([]));
    mockApi.listLocations.and.returnValue(of([]));
    mockApi.getDistinctLocations.and.returnValue(of(['L1']));
    mockApi.getDistinctDataTypes.and.returnValue(of(['D1']));
    mockApi.getDistinctTesterTypes.and.returnValue(of(['T1']));

    TestBed.configureTestingModule({
      imports: [DiscoveryComponent],
      providers: [{ provide: BackendService, useValue: mockApi }]
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DiscoveryComponent as any);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should call distinct endpoints when instance changes', (done) => {
    component.selectedInstanceKey = 'SAMPLE_KEY';
    component.onInstanceChange();
    // debounceTime is 250ms, wait a bit longer
    setTimeout(() => {
      expect(mockApi.getDistinctLocations).toHaveBeenCalled();
      expect(mockApi.getDistinctDataTypes).toHaveBeenCalled();
      expect(mockApi.getDistinctTesterTypes).toHaveBeenCalled();
      done();
    }, 400);
  });

  it('should call distinct endpoints when filters change', (done) => {
    component.selectedInstanceKey = 'SAMPLE_KEY';
    // simulate instance set and initial call
    component.onInstanceChange();
    setTimeout(() => {
      mockApi.getDistinctLocations.calls.reset();
      mockApi.getDistinctDataTypes.calls.reset();
      mockApi.getDistinctTesterTypes.calls.reset();
      // change a filter
      component.dataType = 'DT';
      component.onFilterChange();
      setTimeout(() => {
        expect(mockApi.getDistinctLocations).toHaveBeenCalled();
        expect(mockApi.getDistinctDataTypes).toHaveBeenCalled();
        expect(mockApi.getDistinctTesterTypes).toHaveBeenCalled();
        done();
      }, 400);
    }, 400);
  });
});
