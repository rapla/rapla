import { beforeEach, afterEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ProfileService, ProfileEditCapabilities } from './profile.service';

describe('ProfileService', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ProfileService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs profile capabilities', () => {
    const caps: ProfileEditCapabilities = {
      canChangePassword: true, canChangeName: true, canChangeEmail: true, externalIdpLabel: null,
    };
    let received: ProfileEditCapabilities | undefined;
    service.capabilities().subscribe((r) => (received = r));
    const req = http.expectOne('/api/storage/profile/capabilities');
    expect(req.request.method).toBe('GET');
    req.flush(caps);
    expect(received).toEqual(caps);
  });

  it('POSTs a name change with the three-part body', () => {
    service.changeName('homer', 'Mr', 'Homer', 'Simpson').subscribe();
    const req = http.expectOne('/api/storage/change/name');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      username: 'homer', newTitle: 'Mr', newSurename: 'Homer', newLastname: 'Simpson',
    });
    req.flush(null);
  });

  it('POSTs an email change', () => {
    service.changeEmail('homer', 'homer@example.com').subscribe();
    const req = http.expectOne('/api/storage/change/email');
    expect(req.request.body).toEqual({ username: 'homer', newEmail: 'homer@example.com' });
    req.flush(null);
  });

  it('POSTs a password change', () => {
    service.changePassword('homer', 'old', 'new').subscribe();
    const req = http.expectOne('/api/storage/change/password');
    expect(req.request.body).toEqual({ username: 'homer', oldPassword: 'old', newPassword: 'new' });
    req.flush(null);
  });
});
