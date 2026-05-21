import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from './auth.service';
import { UsersService, UserSummary } from './users.service';

/**
 * PRD 051 — "Switch to user" dialog. Opens from the username chip
 * in the toolbar (only clickable when the caller can admin ≥1 user).
 * Material autocomplete backed by {@code GET /api/users}, which is
 * server-side filtered to the caller's admin scope. On submit:
 * calls {@link AuthService.impersonate} and closes; the toolbar
 * re-renders against the new effective identity (see PRD 051's
 * impersonation override flow).
 *
 * <p>Inline error if the impersonate call returns false (the user
 * was deleted between fetch + submit, or some race we don't want
 * to assert against). The dialog stays open so the admin can pick a
 * different target without re-launching.
 */
@Component({
  selector: 'app-switch-to-user-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="icon" aria-hidden="true">person_search</mat-icon>
      Switch to user
    </h2>
    <mat-dialog-content>
      @if (loading()) {
        <div class="centered"><mat-spinner diameter="28"></mat-spinner></div>
      } @else if (users().length === 0) {
        <p class="hint">No users found in your admin scope.</p>
      } @else {
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Target user</mat-label>
          <input
            type="text"
            matInput
            [formControl]="usernameControl"
            [matAutocomplete]="auto"
            placeholder="Type a username…"
          />
          <mat-autocomplete #auto="matAutocomplete" [displayWith]="displayUser">
            @for (user of filtered(); track user.username) {
              <mat-option [value]="user.username">
                <span class="user-username">{{ user.username }}</span>
                @if (user.displayName) {
                  <span class="user-display">— {{ user.displayName }}</span>
                }
              </mat-option>
            }
          </mat-autocomplete>
        </mat-form-field>
      }
      @if (errorMessage()) {
        <p class="error">{{ errorMessage() }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton mat-dialog-close>Cancel</button>
      <button
        matButton="filled"
        cdkFocusInitial
        [disabled]="!canSubmit() || submitting()"
        (click)="submit()"
      >
        @if (submitting()) {
          <mat-spinner diameter="16"></mat-spinner>
        } @else {
          Switch
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      h2 {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .icon {
        color: #f57f17;
      }
      .full-width {
        width: 100%;
      }
      .centered {
        display: flex;
        justify-content: center;
        padding: 1rem;
      }
      .hint {
        color: rgba(0, 0, 0, 0.6);
        font-size: 0.9rem;
      }
      .user-username {
        font-weight: 500;
      }
      .user-display {
        margin-left: 0.4rem;
        color: rgba(0, 0, 0, 0.6);
      }
      .error {
        color: #c62828;
        font-size: 0.9rem;
        margin-top: 0.5rem;
      }
    `,
  ],
})
export class SwitchToUserDialogComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly usersService = inject(UsersService);
  private readonly ref = inject<MatDialogRef<SwitchToUserDialogComponent>>(MatDialogRef);

  readonly usernameControl = new FormControl<string>('', { nonNullable: true });
  readonly users = signal<UserSummary[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Mirror of {@link usernameControl}'s value as a signal so the
   *  {@link filtered} and {@link canSubmit} computeds recompute on
   *  every keystroke. FormControl.value alone isn't reactive in the
   *  signal sense. */
  readonly inputValue = signal('');

  /** Live filter on the input value. Case-insensitive substring match
   *  against username or displayName. */
  readonly filtered = computed(() => {
    const query = this.inputValue().trim().toLowerCase();
    const all = this.users();
    if (!query) return all;
    return all.filter(
      (u) =>
        u.username.toLowerCase().includes(query) ||
        (u.displayName?.toLowerCase() ?? '').includes(query),
    );
  });

  /** Enabled when the input value matches a known username exactly
   *  (autocomplete-pick OR manual exact-match typed by an admin who
   *  remembers the name). Prevents submitting random typos. */
  readonly canSubmit = computed(() => {
    const value = this.inputValue().trim();
    if (!value) return false;
    return this.users().some((u) => u.username === value);
  });

  ngOnInit() {
    this.usersService.list().subscribe((list) => {
      this.users.set(list);
      this.loading.set(false);
    });
    // Sync FormControl → signal so the computeds recompute on input.
    this.usernameControl.valueChanges.subscribe((v) => this.inputValue.set(v ?? ''));
  }

  displayUser = (value: string | null): string => value ?? '';

  async submit(): Promise<void> {
    const target = (this.usernameControl.value ?? '').trim();
    if (!target) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    const ok = await this.auth.impersonate(target);
    this.submitting.set(false);
    if (ok) {
      this.ref.close({ target });
    } else {
      this.errorMessage.set(
        `Could not impersonate '${target}'. Either the user was removed or you are no longer authorised — pick a different target.`,
      );
    }
  }
}
