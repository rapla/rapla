import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { PermissionMigrationDialogComponent } from './permission-migration-dialog.component';

// Wire shape of the REST DTO org.rapla.rest.dto.PermissionMigrationFinding (PRD 116 renamed only the GraphQL wire).
const SERVER_FINDING = {
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
};

describe('PermissionMigrationDialogComponent', () => {
  it('shows the resource name of a server finding and resolves it by its id', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PermissionMigrationDialogComponent],
      providers: [provideAnimationsAsync(), provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PermissionMigrationDialogComponent);
    fixture.detectChanges();
    http.expectOne('/api/admin/permission-migration/findings').flush([SERVER_FINDING]);
    fixture.detectChanges();

    const name = (fixture.nativeElement as HTMLElement).querySelector('.fname');
    expect(name?.textContent?.trim()).toBe('Room A');

    const c = fixture.componentInstance;
    c.resolve(c.findings()[0]);
    http.expectOne('/api/admin/permission-migration/a1/resolve').flush([]);
    http.verify();
  });
});
