import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';

import { RemoteStorageControllerService } from '../api/api/remote-storage-controller.service';
import { AuthService } from '../auth/auth.service';

interface ResourceLite {
  id: string;
  label: string;
}

interface AppointmentLite {
  start: string;
  end: string;
  reservationName: string;
  allocatables: string[];
}

@Component({
  selector: 'app-reservations',
  imports: [DatePipe],
  template: `
    <header>
      <h1>Reservations</h1>
      <button type="button" (click)="auth.logout()">Sign out</button>
    </header>

    @if (loading()) {
      <p>Loading…</p>
    } @else if (error()) {
      <p class="error">{{ error() }}</p>
    } @else {
      <p class="meta">
        {{ rows().length }} appointment(s),
        {{ resourceCount() }} resource(s) hydrated.
      </p>
      <table>
        <thead>
          <tr>
            <th>Start</th>
            <th>End</th>
            <th>Event</th>
            <th>Allocatables</th>
          </tr>
        </thead>
        <tbody>
          @for (r of rows(); track r.start + r.reservationName) {
            <tr>
              <td>{{ r.start | date:'medium' }}</td>
              <td>{{ r.end | date:'medium' }}</td>
              <td>{{ r.reservationName }}</td>
              <td>{{ r.allocatables.join(', ') }}</td>
            </tr>
          } @empty {
            <tr><td colspan="4" class="empty">No reservations in the queried window.</td></tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: [`
    :host { display: block; font-family: system-ui, sans-serif; max-width: 1100px; margin: 2rem auto; padding: 0 1rem; }
    header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    h1 { font-size: 1.5rem; margin: 0; }
    button { padding: 0.4rem 0.8rem; border: 1px solid #ccc; background: white; border-radius: 4px; cursor: pointer; }
    .meta { color: #666; font-size: 0.85rem; margin-bottom: 0.5rem; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.5rem; border-bottom: 1px solid #eee; text-align: left; font-size: 0.9rem; }
    th { background: #f8f9fa; font-weight: 600; }
    .empty { color: #888; text-align: center; font-style: italic; }
    .error { color: #dc2626; }
  `]
})
export class ReservationsComponent implements OnInit {
  private readonly api = inject(RemoteStorageControllerService);
  protected readonly auth = inject(AuthService);

  loading = signal(true);
  error = signal<string | null>(null);
  rows = signal<AppointmentLite[]>([]);
  resourceCount = signal(0);

  ngOnInit() {
    const now = new Date();
    const start = new Date(now.getFullYear() - 1, 0, 1).toISOString().replace(/\.\d{3}Z$/, '');
    const end = new Date(now.getFullYear() + 2, 11, 31).toISOString().replace(/\.\d{3}Z$/, '');

    forkJoin({
      bundle: this.api.getResources(),
      appts: this.api.queryAppointments({ start, end })
    }).subscribe({
      next: ({ bundle, appts }) => {
        const resources = this.buildResourceIndex(bundle);
        this.resourceCount.set(resources.size);
        this.rows.set(this.flattenAppointments(appts, resources));
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? `Request failed (HTTP ${err?.status ?? '?'})`);
        this.loading.set(false);
      }
    });
  }

  private buildResourceIndex(bundle: any): Map<string, string> {
    const out = new Map<string, string>();
    for (const r of (bundle?.resources ?? []) as any[]) {
      out.set(r.id, this.labelFor(r));
    }
    return out;
  }

  private labelFor(resource: any): string {
    const data = resource?.classification?.data ?? {};
    for (const key of ['name', 'surname', 'firstname', 'title']) {
      const v = data[key];
      if (Array.isArray(v) && v[0]) return String(v[0]);
    }
    const firstKey = Object.keys(data)[0];
    const v = firstKey ? data[firstKey] : null;
    return Array.isArray(v) && v[0] ? String(v[0]) : resource.id;
  }

  private flattenAppointments(payload: any, resources: Map<string, string>): AppointmentLite[] {
    const result: AppointmentLite[] = [];
    const reservations = (payload?.reservations ?? []) as any[];
    for (const res of reservations) {
      const name = this.labelFor(res) ?? '(no name)';
      const allocIds: string[] = (res?.links?.allocatable ?? []) as string[];
      const allocNames = allocIds.map((id) => resources.get(id) ?? id);
      for (const appt of (res.appointments ?? []) as any[]) {
        result.push({
          start: appt.start,
          end: appt.end,
          reservationName: name,
          allocatables: allocNames
        });
      }
    }
    return result.sort((a, b) => (a.start < b.start ? -1 : 1));
  }
}
