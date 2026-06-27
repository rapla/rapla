import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../auth/auth.service';
import { ProfileService } from './profile.service';

/**
 * PRD 050 — "Edit account" dialog (local users only). Reached from the user
 * menu's Account settings submenu; the menu hides the entry entirely when the
 * user is externally provisioned, but this dialog also gates each section on
 * the server's {@code ProfileEditCapabilities} so a provisioned user who
 * reaches it anyway sees a read-only notice rather than failing writes.
 *
 * Three independently-savable sections — name (title / first / last), email,
 * password — each posting to the existing provisioning-aware change endpoints.
 */
@Component({
  selector: 'app-edit-account-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="title-icon" aria-hidden="true">badge</mat-icon>
      Edit account
    </h2>
    <mat-dialog-content>
      @if (loading()) {
        <div class="centered"><mat-spinner diameter="28"></mat-spinner></div>
      } @else {
        @if (externalIdpLabel()) {
          <div class="banner">
            <mat-icon aria-hidden="true">info</mat-icon>
            <div>
              Your profile is managed by your identity provider
              (<b>{{ externalIdpLabel() }}</b>). Name, e-mail and password are read-only here.
            </div>
          </div>
        }

        @if (canChangeName()) {
          <section [formGroup]="nameForm">
            <h3>Name <span class="hint">(current: {{ currentName() }})</span></h3>
            <div class="name-row">
              <mat-form-field appearance="outline" class="title-field">
                <mat-label>Title</mat-label>
                <input matInput formControlName="title" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>First name</mat-label>
                <input matInput formControlName="firstname" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label>Last name</mat-label>
                <input matInput formControlName="lastname" />
              </mat-form-field>
            </div>
            <button matButton="filled" [disabled]="busy() === 'name'" (click)="saveName()">
              Save name
            </button>
          </section>
        }

        @if (canChangeEmail()) {
          <section [formGroup]="emailForm">
            <h3>E-mail</h3>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New e-mail</mat-label>
              <input matInput type="email" formControlName="email" />
            </mat-form-field>
            <button matButton="filled" [disabled]="emailForm.invalid || busy() === 'email'" (click)="saveEmail()">
              Save e-mail
            </button>
          </section>
        }

        @if (canChangePassword()) {
          <section [formGroup]="passwordForm">
            <h3>Password</h3>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Current password</mat-label>
              <input matInput type="password" formControlName="oldPassword" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New password</mat-label>
              <input matInput type="password" formControlName="newPassword" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm new password</mat-label>
              <input matInput type="password" formControlName="confirm" />
            </mat-form-field>
            <button
              matButton="filled"
              [disabled]="passwordForm.invalid || !passwordsMatch() || busy() === 'password'"
              (click)="savePassword()"
            >
              Change password
            </button>
            @if (passwordForm.value.confirm && !passwordsMatch()) {
              <p class="error">Passwords do not match.</p>
            }
          </section>
        }

        @if (successMessage()) {
          <p class="success">{{ successMessage() }}</p>
        }
        @if (errorMessage()) {
          <p class="error">{{ errorMessage() }}</p>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      h2 { display: flex; align-items: center; gap: 0.5rem; }
      .title-icon { color: #1565c0; }
      section { border-top: 1px solid rgba(0, 0, 0, 0.08); padding-top: 0.75rem; margin-top: 0.5rem; }
      section:first-of-type { border-top: none; }
      h3 { font-size: 0.95rem; margin: 0 0 0.5rem; }
      .hint { font-weight: 400; color: rgba(0, 0, 0, 0.55); font-size: 0.8rem; }
      .full-width { width: 100%; }
      .name-row { display: flex; gap: 0.5rem; }
      .name-row mat-form-field { flex: 1; }
      .title-field { flex: 0 0 6rem; }
      .centered { display: flex; justify-content: center; padding: 1rem; }
      .banner { display: flex; gap: 0.6rem; align-items: flex-start; background: #fff8e1; border: 1px solid #ffe082; border-radius: 8px; padding: 0.75rem; font-size: 0.85rem; margin-bottom: 1rem; }
      .banner mat-icon { color: #f9a825; }
      .error { color: #c62828; font-size: 0.9rem; margin-top: 0.4rem; }
      .success { color: #2e7d32; font-size: 0.9rem; margin-top: 0.4rem; }
    `,
  ],
})
export class EditAccountDialogComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly profile = inject(ProfileService);

  readonly loading = signal(true);
  readonly busy = signal<string | null>(null);
  readonly externalIdpLabel = signal<string | null>(null);
  readonly canChangeName = signal(false);
  readonly canChangeEmail = signal(false);
  readonly canChangePassword = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly currentName = signal('');

  readonly nameForm = new FormGroup({
    title: new FormControl('', { nonNullable: true }),
    firstname: new FormControl('', { nonNullable: true }),
    lastname: new FormControl('', { nonNullable: true }),
  });
  readonly emailForm = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });
  readonly passwordForm = new FormGroup({
    oldPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    newPassword: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(1)] }),
    confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  private get username(): string {
    return this.auth.identity()?.username ?? '';
  }

  ngOnInit(): void {
    this.currentName.set(this.auth.identity()?.name ?? '');
    this.profile.capabilities().subscribe({
      next: (caps) => {
        this.externalIdpLabel.set(caps.externalIdpLabel);
        this.canChangeName.set(caps.canChangeName);
        this.canChangeEmail.set(caps.canChangeEmail);
        this.canChangePassword.set(caps.canChangePassword);
        this.loading.set(false);
      },
      error: (err) => {
        console.warn('[edit-account] capabilities failed', err);
        this.loading.set(false);
        this.errorMessage.set('Could not load account capabilities.');
      },
    });
  }

  passwordsMatch(): boolean {
    const { newPassword, confirm } = this.passwordForm.value;
    return !!newPassword && newPassword === confirm;
  }

  saveName(): void {
    this.run('name', this.profile.changeName(
      this.username,
      this.nameForm.controls.title.value,
      this.nameForm.controls.firstname.value,
      this.nameForm.controls.lastname.value,
    ), 'Name updated.', () => this.currentName.set(
      [this.nameForm.controls.title.value, this.nameForm.controls.firstname.value, this.nameForm.controls.lastname.value]
        .filter((s) => s.trim()).join(' '),
    ));
  }

  saveEmail(): void {
    if (this.emailForm.invalid) return;
    this.run('email', this.profile.changeEmail(this.username, this.emailForm.controls.email.value), 'E-mail updated.');
  }

  savePassword(): void {
    if (this.passwordForm.invalid || !this.passwordsMatch()) return;
    this.run(
      'password',
      this.profile.changePassword(
        this.username,
        this.passwordForm.controls.oldPassword.value,
        this.passwordForm.controls.newPassword.value,
      ),
      'Password changed.',
      () => this.passwordForm.reset(),
    );
  }

  private run(section: string, call: import('rxjs').Observable<void>, ok: string, after?: () => void): void {
    this.busy.set(section);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    call.subscribe({
      next: () => {
        this.busy.set(null);
        this.successMessage.set(ok);
        after?.();
      },
      error: (err) => {
        this.busy.set(null);
        const serverMsg = err?.error?.message ?? err?.error ?? null;
        this.errorMessage.set(typeof serverMsg === 'string' && serverMsg ? serverMsg : 'Update failed.');
      },
    });
  }
}
