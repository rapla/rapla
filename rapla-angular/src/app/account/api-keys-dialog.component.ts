import { Component, OnInit, inject, signal } from '@angular/core';
import { SlicePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

import { API_KEY_SCOPES, ApiKeyMetadata, ApiKeysService } from './api-keys.service';

/**
 * PRD 043 / 076 — "Manage API keys" dialog, reached from the user menu's
 * Account settings submenu. Lists the caller's keys (label / created / expiry /
 * scope chips), creates a new key (server mints + returns the bearer JWT ONCE,
 * shown here in a copy-once box), rotates a {@code rotate_self} key, and revokes.
 *
 * The secret is held in {@link freshSecret} only until the dialog is closed —
 * never re-fetchable (the server keeps the public half only).
 */
@Component({
  selector: 'app-api-keys-dialog',
  imports: [
    SlicePipe,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="title-icon" aria-hidden="true">key</mat-icon>
      API keys
    </h2>
    <mat-dialog-content>
      @if (freshSecret(); as secret) {
        <div class="secret-box">
          <div class="secret-head">
            <mat-icon aria-hidden="true">check_circle</mat-icon>
            <span>Copy your key now — it is shown only once.</span>
          </div>
          <code class="secret">{{ secret }}</code>
          <button matButton (click)="copy(secret)">
            <mat-icon>content_copy</mat-icon> {{ copied() ? 'Copied' : 'Copy' }}
          </button>
        </div>
      }

      @if (loading()) {
        <div class="centered"><mat-spinner diameter="28"></mat-spinner></div>
      } @else {
        @if (keys().length === 0) {
          <p class="hint">No API keys yet.</p>
        }
        @for (k of keys(); track k.id) {
          <div class="key-row">
            <mat-icon class="key-icon" aria-hidden="true">vpn_key</mat-icon>
            <div class="kmeta">
              <div class="klabel">{{ k.label || '(unlabeled)' }}</div>
              <div class="ksub">
                Created {{ k.createdAt | slice: 0 : 10 }} · {{ expiryLabel(k.expiresAt) }} · …{{
                  k.thumbprint | slice: -4
                }}
              </div>
              <div class="scopes">
                @for (s of k.scopes; track s) {
                  <span class="scope" [class.write]="isWrite(s)">{{ s }}</span>
                }
              </div>
            </div>
            @if (isRotatable(k)) {
              <button
                matIconButton
                matTooltip="Rotate — issue a fresh key with the same scopes; the old one expires after a grace window"
                (click)="startRotate(k)"
                [disabled]="busyId() === k.id"
              >
                <mat-icon>autorenew</mat-icon>
              </button>
            }
            <button
              matIconButton
              matTooltip="Revoke"
              (click)="revoke(k)"
              [disabled]="busyId() === k.id"
            >
              <mat-icon>delete</mat-icon>
            </button>
          </div>
          @if (rotatingId() === k.id) {
            <div class="rotate-bar">
              <span class="rotate-hint">Old key stays valid for a grace window, then expires.</span>
              <mat-form-field appearance="outline" class="grace-field" subscriptSizing="dynamic">
                <mat-label>Grace (minutes)</mat-label>
                <input matInput type="number" min="0" max="2880" [formControl]="graceControl" />
              </mat-form-field>
              <button matButton (click)="cancelRotate()">Cancel</button>
              <button
                matButton="filled"
                [disabled]="graceControl.invalid || busyId() === k.id"
                (click)="confirmRotate(k)"
              >
                Rotate
              </button>
            </div>
          }
        }
      }

      @if (errorMessage()) {
        <p class="error">{{ errorMessage() }}</p>
      }

      @if (creating()) {
        <form [formGroup]="form" class="create-form" (ngSubmit)="submitCreate()">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Label</mat-label>
            <input matInput formControlName="label" placeholder="e.g. CI export job" />
          </mat-form-field>
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Expires in days (blank = never)</mat-label>
            <input matInput type="number" min="1" formControlName="expiresInDays" />
          </mat-form-field>
          <div class="scope-pick">
            <span class="scope-label">Scopes</span>
            @for (s of allScopes; track s) {
              <mat-checkbox
                [checked]="selectedScopes().includes(s)"
                [disabled]="s === 'read'"
                (change)="toggleScope(s, $event.checked)"
              >
                {{ s }}
              </mat-checkbox>
            }
          </div>
        </form>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton mat-dialog-close>Close</button>
      @if (creating()) {
        <button matButton (click)="cancelCreate()">Cancel</button>
        <button
          matButton="filled"
          [disabled]="form.invalid || busyId() === 'new'"
          (click)="submitCreate()"
        >
          @if (busyId() === 'new') {
            <mat-spinner diameter="16"></mat-spinner>
          } @else {
            Create key
          }
        </button>
      } @else {
        <button matButton="filled" (click)="startCreate()"><mat-icon>add</mat-icon> New key</button>
      }
    </mat-dialog-actions>
  `,
  styles: [
    `
      h2 {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .title-icon {
        color: #1565c0;
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
      .error {
        color: #c62828;
        font-size: 0.9rem;
        margin-top: 0.5rem;
      }
      .key-row {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.6rem 0;
        border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      }
      .key-row:last-of-type {
        border-bottom: none;
      }
      .key-icon {
        color: #1565c0;
      }
      .kmeta {
        flex: 1;
      }
      .klabel {
        font-weight: 500;
      }
      .ksub {
        font-size: 0.78rem;
        color: rgba(0, 0, 0, 0.6);
      }
      .scopes {
        display: flex;
        gap: 0.35rem;
        flex-wrap: wrap;
        margin-top: 0.25rem;
      }
      .scope {
        font-size: 0.7rem;
        background: #e8eef7;
        color: #0d47a1;
        border-radius: 10px;
        padding: 0.1rem 0.5rem;
      }
      .scope.write {
        background: #fdecea;
        color: #c62828;
      }
      .secret-box {
        background: #f1f8e9;
        border: 1px solid #aed581;
        border-radius: 8px;
        padding: 0.75rem;
        margin-bottom: 1rem;
      }
      .secret-head {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        color: #33691e;
        font-size: 0.85rem;
        margin-bottom: 0.5rem;
      }
      .secret {
        display: block;
        word-break: break-all;
        font-size: 0.72rem;
        background: #fff;
        border-radius: 4px;
        padding: 0.5rem;
        margin-bottom: 0.5rem;
      }
      .rotate-bar {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        flex-wrap: wrap;
        padding: 0 0 0.6rem 2rem;
      }
      .rotate-hint {
        font-size: 0.78rem;
        color: rgba(0, 0, 0, 0.6);
        flex: 1 1 12rem;
      }
      .grace-field {
        width: 9rem;
      }
      .create-form {
        margin-top: 1rem;
      }
      .scope-pick {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }
      .scope-label {
        font-size: 0.78rem;
        color: rgba(0, 0, 0, 0.6);
        margin-bottom: 0.2rem;
      }
    `,
  ],
})
export class ApiKeysDialogComponent implements OnInit {
  private readonly api = inject(ApiKeysService);

  /** Default key lifetime. Short by design — rotation (one click) makes it cheap. */
  static readonly DEFAULT_EXPIRY_DAYS = 180;
  /** Default rotation grace window (minutes); server default + cap mirror PRD 076 D7. */
  static readonly DEFAULT_GRACE_MINUTES = 180;
  static readonly MAX_GRACE_MINUTES = 2880; // 2 days

  readonly allScopes = API_KEY_SCOPES;

  readonly keys = signal<ApiKeyMetadata[]>([]);
  readonly loading = signal(true);
  readonly creating = signal(false);
  readonly busyId = signal<string | null>(null);
  readonly freshSecret = signal<string | null>(null);
  readonly copied = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly selectedScopes = signal<string[]>(['read']);

  /** The key row whose inline rotate (grace) editor is open, or null. */
  readonly rotatingId = signal<string | null>(null);

  readonly form = new FormGroup({
    label: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    expiresInDays: new FormControl<number | null>(ApiKeysDialogComponent.DEFAULT_EXPIRY_DAYS),
  });

  readonly graceControl = new FormControl<number>(ApiKeysDialogComponent.DEFAULT_GRACE_MINUTES, {
    nonNullable: true,
    validators: [
      Validators.required,
      Validators.min(0),
      Validators.max(ApiKeysDialogComponent.MAX_GRACE_MINUTES),
    ],
  });

  ngOnInit(): void {
    this.reload();
  }

  isWrite(scope: string): boolean {
    return scope.startsWith('write');
  }

  /**
   * D15 (Model B) — a key is rotatable only if it holds `rotate_self`, the SAME rule the server
   * enforces for both human and machine callers. A read-only key shows no rotate button (delete +
   * create a fresh one instead). After rotation the old key sheds `rotate_self` (D13), so its
   * button disappears too — only the latest token in a chain is rotatable.
   */
  isRotatable(k: ApiKeyMetadata): boolean {
    return k.scopes.includes('rotate_self');
  }

  /**
   * Relative expiry: "expires in N min" under a day (so a grace-windowed key reads as minutes,
   * not a same-day date), "expires in N days" otherwise. {@code null} → "no expiry".
   */
  expiryLabel(expiresAt: string | null): string {
    if (!expiresAt) return 'no expiry';
    const ms = new Date(expiresAt).getTime() - Date.now();
    if (ms <= 0) return 'expired';
    const days = Math.floor(ms / 86_400_000);
    if (days >= 1) return `expires in ${days} day${days === 1 ? '' : 's'}`;
    const minutes = Math.max(1, Math.round(ms / 60_000));
    return `expires in ${minutes} min`;
  }

  startCreate(): void {
    this.errorMessage.set(null);
    this.selectedScopes.set(['read']);
    this.form.reset({ label: '', expiresInDays: ApiKeysDialogComponent.DEFAULT_EXPIRY_DAYS });
    this.creating.set(true);
  }

  cancelCreate(): void {
    this.creating.set(false);
  }

  toggleScope(scope: string, checked: boolean): void {
    const next = new Set(this.selectedScopes());
    if (checked) next.add(scope);
    else next.delete(scope);
    next.add('read'); // read is the baseline floor (server normalises it in anyway)
    this.selectedScopes.set([...next]);
  }

  submitCreate(): void {
    if (this.form.invalid) return;
    this.busyId.set('new');
    this.errorMessage.set(null);
    this.api
      .create({
        label: this.form.controls.label.value,
        expiresInDays: this.form.controls.expiresInDays.value,
        scopes: this.selectedScopes(),
      })
      .subscribe({
        next: (created) => {
          this.freshSecret.set(created.key);
          this.copied.set(false);
          this.creating.set(false);
          this.busyId.set(null);
          this.reload();
        },
        error: (err) => this.fail('Could not create the key.', err),
      });
  }

  /** Open the inline grace editor for a key row (reset to the default grace window). */
  startRotate(k: ApiKeyMetadata): void {
    this.errorMessage.set(null);
    this.graceControl.setValue(ApiKeysDialogComponent.DEFAULT_GRACE_MINUTES);
    this.rotatingId.set(k.id);
  }

  cancelRotate(): void {
    this.rotatingId.set(null);
  }

  /**
   * Server-side rotation (PRD 076 Phase 6 / D12): one call mints a fresh same-scope successor and
   * grace-expires the old key. The old credential keeps working for {@code graceMinutes} so a live
   * integration isn't broken the instant you rotate. The new key is shown once to copy.
   */
  confirmRotate(k: ApiKeyMetadata): void {
    if (this.graceControl.invalid) return;
    this.busyId.set(k.id);
    this.errorMessage.set(null);
    this.api.rotate(k.id, this.graceControl.value).subscribe({
      next: (created) => {
        this.freshSecret.set(created.key);
        this.copied.set(false);
        this.rotatingId.set(null);
        this.busyId.set(null);
        this.reload();
      },
      error: (err) => {
        // 409 — a prior grace key in this chain is still alive (D14 max-2 cap).
        const msg =
          err?.status === 409
            ? 'A previous key from an earlier rotation is still in its grace window. Revoke it first — only two keys per rotation are allowed at once.'
            : 'Could not rotate the key.';
        this.fail(msg, err);
      },
    });
  }

  revoke(k: ApiKeyMetadata): void {
    this.busyId.set(k.id);
    this.errorMessage.set(null);
    this.api.revoke(k.id).subscribe({
      next: () => {
        this.busyId.set(null);
        this.reload();
      },
      error: (err) => this.fail('Could not revoke the key.', err),
    });
  }

  async copy(secret: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(secret);
      this.copied.set(true);
    } catch {
      this.copied.set(false);
    }
  }

  private reload(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (list) => {
        this.keys.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.fail('Could not load API keys.', err);
      },
    });
  }

  private fail(message: string, err: unknown): void {
    console.warn('[api-keys]', message, err);
    this.busyId.set(null);
    this.errorMessage.set(message);
  }
}
