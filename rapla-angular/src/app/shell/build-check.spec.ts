import { DOCUMENT, Injector, runInInjectionContext } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  BUILD_HEADER,
  BuildCheckService,
  MISMATCH_HEADER,
  buildCheckInterceptor,
  clientBuild,
} from './build-check';

function docWith(...srcs: string[]): Document {
  const doc = document.implementation.createHTMLDocument('t');
  for (const src of srcs) {
    const s = doc.createElement('script');
    s.setAttribute('src', src);
    doc.body.appendChild(s);
  }
  return doc;
}

function service(build: string, answer: boolean) {
  const opened: unknown[] = [];
  const dialog = {
    open: (c: unknown) => {
      opened.push(c);
      return { afterClosed: () => of(answer) };
    },
  };
  const injector = Injector.create({
    providers: [
      { provide: MatDialog, useValue: dialog },
      { provide: DOCUMENT, useValue: docWith(build === 'dev' ? 'main.js' : build) },
    ],
  });
  const svc = runInInjectionContext(injector, () => new BuildCheckService());
  let reloads = 0;
  svc.reload = () => reloads++;
  return { svc, opened, reloads: () => reloads };
}

describe('clientBuild', () => {
  it('reads the hashed main bundle name', () => {
    expect(clientBuild(docWith('polyfills-AB.js', 'main-CgJ_i-t9.js'))).toBe('main-CgJ_i-t9.js');
  });

  it('is dev without a hashed main bundle (ng serve)', () => {
    expect(clientBuild(docWith('main.js'))).toBe('dev');
  });
});

describe('BuildCheckService', () => {
  it('asks once per differing server build and reloads on confirm', () => {
    const t = service('main-AAAA1111.js', true);
    t.svc.onServerBuild('main-BBBB2222.js');
    t.svc.onServerBuild('main-BBBB2222.js');
    expect(t.opened.length).toBe(1);
    expect(t.reloads()).toBe(1);
  });

  it('keeps working on cancel and asks again only for another build', () => {
    const t = service('main-AAAA1111.js', false);
    t.svc.onServerBuild('main-BBBB2222.js');
    t.svc.onServerBuild('main-BBBB2222.js');
    t.svc.onServerBuild('main-CCCC3333.js');
    expect(t.opened.length).toBe(2);
    expect(t.reloads()).toBe(0);
  });

  it('ignores a missing header, the own build and dev clients', () => {
    const t = service('main-AAAA1111.js', true);
    t.svc.onServerBuild(null);
    t.svc.onServerBuild('main-AAAA1111.js');
    const dev = service('dev', true);
    dev.svc.onServerBuild('main-BBBB2222.js');
    expect(t.opened.length + dev.opened.length).toBe(0);
  });
});

describe('buildCheckInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const seen: (string | null)[] = [];

  beforeEach(() => {
    seen.length = 0;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([buildCheckInterceptor])),
        provideHttpClientTesting(),
        {
          provide: BuildCheckService,
          useValue: {
            build: 'main-AAAA1111.js',
            onServerBuild: (b: string | null) => seen.push(b),
          },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('sends the own build on /api requests and reports the mismatch header', () => {
    http.get('/api/x').subscribe();
    const req = backend.expectOne('/api/x');
    expect(req.request.headers.get(BUILD_HEADER)).toBe('main-AAAA1111.js');
    req.flush({}, { headers: { [MISMATCH_HEADER]: 'main-BBBB2222.js' } });
    expect(seen).toEqual(['main-BBBB2222.js']);
  });

  it('reports the mismatch header of an error response', () => {
    http.get('/api/x').subscribe({ error: () => undefined });
    backend.expectOne('/api/x').flush('boom', {
      status: 500,
      statusText: 'x',
      headers: { [MISMATCH_HEADER]: 'main-BBBB2222.js' },
    });
    expect(seen).toEqual(['main-BBBB2222.js']);
  });

  it('leaves non-api requests untouched', () => {
    http.get('/app/x').subscribe();
    const req = backend.expectOne('/app/x');
    expect(req.request.headers.has(BUILD_HEADER)).toBe(false);
    req.flush({});
    expect(seen).toEqual([]);
  });
});
