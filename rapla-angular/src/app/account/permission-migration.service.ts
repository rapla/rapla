import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * PRD 090 — client for the admin additive-permission migration worklist
 * ({@code /api/admin/permission-migration}). Admin-only; cookie-auth same-origin.
 *
 * Each finding is one allocatable whose effective access rose when resolution
 * went additive (a soft deny stopped biting); {@link resolve} prunes the inert
 * {@code DENIED} rows and acknowledges the rest, dropping it off the list.
 */
export interface PrincipalEscalation {
  principalType: string; // "USER"
  principalName: string;
  currentLevel: string; // e.g. "READ", "DENIED"
  additiveLevel: string; // e.g. "ALLOCATE"
  form: string; // "DENIED" | "SOFT_DENY"
  explanation: string;
}

export interface PermissionMigrationFinding {
  allocatableId: string;
  allocatableName: string;
  escalations: PrincipalEscalation[];
}

@Injectable({ providedIn: 'root' })
export class PermissionMigrationService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/admin/permission-migration';

  findings(): Observable<PermissionMigrationFinding[]> {
    return this.http.get<PermissionMigrationFinding[]>(`${this.base}/findings`);
  }

  resolve(allocatableId: string): Observable<PermissionMigrationFinding[]> {
    return this.http.post<PermissionMigrationFinding[]>(
      `${this.base}/${encodeURIComponent(allocatableId)}/resolve`,
      null,
    );
  }
}
