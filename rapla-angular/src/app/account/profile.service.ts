import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * PRD 050 — self-service profile edits for the SPA. Consumes the existing,
 * provisioning-aware server contract:
 *
 * <ul>
 *   <li>{@code GET /api/storage/profile/capabilities} — what the current user
 *       may edit. {@code externalIdpLabel} is non-null for externally
 *       provisioned (Keycloak / LDAP / …) users, whose name / email / password
 *       live in the IdP; all three {@code canChange*} flags are then false and
 *       the server rejects writes ({@code requireLocalIdentity}).</li>
 *   <li>{@code POST /api/storage/change/name|email|password} — the gated
 *       writes. Each carries the caller's own {@code username}; the server
 *       re-checks self-or-admin and the provisioning guard.</li>
 * </ul>
 */
export interface ProfileEditCapabilities {
  canChangePassword: boolean;
  canChangeName: boolean;
  canChangeEmail: boolean;
  /** null when local; the IdP label ("keycloak" / "ldap" / …) when provisioned. */
  externalIdpLabel: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  capabilities(): Observable<ProfileEditCapabilities> {
    return this.http.get<ProfileEditCapabilities>('/api/storage/profile/capabilities');
  }

  changeName(
    username: string,
    newTitle: string,
    newSurename: string,
    newLastname: string,
  ): Observable<void> {
    return this.http.post<void>('/api/storage/change/name', {
      username,
      newTitle,
      newSurename,
      newLastname,
    });
  }

  changeEmail(username: string, newEmail: string): Observable<void> {
    return this.http.post<void>('/api/storage/change/email', { username, newEmail });
  }

  changePassword(username: string, oldPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/storage/change/password', {
      username,
      oldPassword,
      newPassword,
    });
  }
}
