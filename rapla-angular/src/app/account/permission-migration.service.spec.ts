import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import {
  PermissionMigrationFinding,
  PermissionMigrationService,
} from './permission-migration.service';

describe('PermissionMigrationService', () => {
  let service: PermissionMigrationService;
  let http: HttpTestingController;

  const sample: PermissionMigrationFinding[] = [
    {
      allocatableId: 'a1',
      allocatableName: 'Room A',
      escalations: [
        {
          principalType: 'USER',
          principalName: 'monty',
          currentLevel: 'READ',
          additiveLevel: 'ALLOCATE',
          form: 'SOFT_DENY',
          explanation: 'user monty had only READ and now gains ALLOCATE',
        },
      ],
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PermissionMigrationService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PermissionMigrationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs the worklist findings', () => {
    let received: PermissionMigrationFinding[] | undefined;
    service.findings().subscribe((r) => (received = r));
    const req = http.expectOne('/api/admin/permission-migration/findings');
    expect(req.request.method).toBe('GET');
    req.flush(sample);
    expect(received).toEqual(sample);
  });

  it('POSTs resolve by allocatable id and returns the remaining worklist', () => {
    let received: PermissionMigrationFinding[] | undefined;
    service.resolve('a1').subscribe((r) => (received = r));
    const req = http.expectOne('/api/admin/permission-migration/a1/resolve');
    expect(req.request.method).toBe('POST');
    req.flush([]);
    expect(received).toEqual([]);
  });
});
