import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface AuthErrorDialogData {
  /** Short headline — e.g. "Sign-in rejected" or "Session expired". */
  title: string;
  /** Human-readable explanation — surfaced from the server response when present. */
  message: string;
  /** Optional structured detail surfaced from the response body / WWW-Authenticate. */
  detail?: string;
}

/**
 * Modal shown when an API request returns 401 with a Bearer token attached —
 * i.e. the server actively rejected the token, not just "you aren't logged
 * in yet". Closing the dialog (button click or backdrop dismiss) hands
 * control back to the interceptor, which then clears local state and routes
 * to /login. The dialog deliberately blocks until acknowledged so the user
 * cannot miss the failure reason.
 */
@Component({
  selector: 'app-auth-error-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>
      <mat-icon class="icon" aria-hidden="true">error_outline</mat-icon>
      {{ data.title }}
    </h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      @if (data.detail) {
        <pre class="detail">{{ data.detail }}</pre>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton="filled" mat-dialog-close cdkFocusInitial>Back to sign-in</button>
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
        color: #c62828;
      }
      .detail {
        white-space: pre-wrap;
        background: rgba(0, 0, 0, 0.05);
        padding: 0.5rem;
        border-radius: 3px;
        font-size: 0.85rem;
        max-height: 12rem;
        overflow: auto;
      }
    `,
  ],
})
export class AuthErrorDialogComponent {
  constructor(
    public ref: MatDialogRef<AuthErrorDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: AuthErrorDialogData,
  ) {}
}
