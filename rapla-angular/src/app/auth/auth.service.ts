import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { AuthControllerService } from '../api/api/auth-controller.service';
import { TokenResponse } from '../api/model/token-response';

const TOKEN_KEY = 'rapla.accessToken';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthControllerService);
  private readonly router = inject(Router);

  login(username: string, password: string): Observable<TokenResponse> {
    return this.api.login({ username, password }).pipe(
      tap((res) => {
        if (res.accessToken) {
          localStorage.setItem(TOKEN_KEY, res.accessToken);
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.router.navigateByUrl('/login');
  }

  token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return !!this.token();
  }
}
