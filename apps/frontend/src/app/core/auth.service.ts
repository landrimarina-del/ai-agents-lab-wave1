import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

type LoginResponse = {
  token?: string;
  accessToken?: string;
  jwt?: string;
  role?: string;
  userId?: string | number;
  user?: {
    id?: string | number;
    role?: string;
  };
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'rise_token';
  private readonly roleKey = 'rise_role';
  private readonly userIdKey = 'rise_user_id';

  constructor(private readonly http: HttpClient) {}

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/login', { email, password })
      .pipe(tap((response: LoginResponse) => this.persistSession(response)));
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.roleKey);
    localStorage.removeItem(this.userIdKey);
  }

  isAuthenticated(): boolean {
    return Boolean(this.getToken());
  }

  getToken(): string {
    return localStorage.getItem(this.tokenKey) ?? '';
  }

  getRole(): string {
    return localStorage.getItem(this.roleKey) ?? '';
  }

  getUserId(): string {
    return localStorage.getItem(this.userIdKey) ?? '';
  }

  private persistSession(response: LoginResponse): void {
    const token = response.token ?? response.accessToken ?? response.jwt ?? '';
    const role = response.role ?? response.user?.role ?? '';
    const userIdRaw = response.userId ?? response.user?.id ?? '';

    localStorage.setItem(this.tokenKey, token);
    localStorage.setItem(this.roleKey, role);
    localStorage.setItem(this.userIdKey, String(userIdRaw));
  }
}
