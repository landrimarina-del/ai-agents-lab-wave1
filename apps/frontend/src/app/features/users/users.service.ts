import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export type UserItem = {
  id: string;
  email?: string;
  fullName?: string;
  role?: string;
  active?: boolean;
  status?: string;
  businessUnitIds?: string[];
};

export type CreateUserPayload = {
  email: string;
  fullName: string;
  password: string;
  role: string;
  countryScope?: string[];
};

@Injectable({ providedIn: 'root' })
export class UsersService {
  constructor(private readonly http: HttpClient) {}

  createUser(payload: CreateUserPayload): Observable<unknown> {
    return this.http.post('/api/users', payload);
  }

  listUsers(): Observable<UserItem[]> {
    return this.http
      .get<unknown>('/api/users')
      .pipe(map((response: unknown) => this.extractUsers(response)));
  }

  deactivateUser(userId: string): Observable<unknown> {
    return this.http.patch(`/api/users/${encodeURIComponent(userId)}/deactivate`, {});
  }

  reactivateUser(userId: string): Observable<unknown> {
    return this.http.patch(`/api/users/${encodeURIComponent(userId)}/reactivate`, {});
  }

  updateBusinessUnits(userId: string, businessUnitIds: string[]): Observable<unknown> {
    return this.http.patch(`/api/users/${encodeURIComponent(userId)}/business-units`, {
      businessUnitIds
    });
  }

  private extractUsers(response: unknown): UserItem[] {
    if (Array.isArray(response)) {
      return response.map((entry: unknown) => this.normalizeUser(entry)).filter((entry): entry is UserItem => entry !== null);
    }

    if (this.isRecord(response)) {
      const candidates = [response['items'], response['data'], response['users']];
      for (const candidate of candidates) {
        if (Array.isArray(candidate)) {
          return candidate
            .map((entry: unknown) => this.normalizeUser(entry))
            .filter((entry): entry is UserItem => entry !== null);
        }
      }
    }

    return [];
  }

  private normalizeUser(value: unknown): UserItem | null {
    if (!this.isRecord(value)) {
      return null;
    }

    const idRaw = value['id'] ?? value['userId'];
    if (typeof idRaw !== 'string' && typeof idRaw !== 'number') {
      return null;
    }

    const role = typeof value['role'] === 'string' ? value['role'] : undefined;
    const email = typeof value['email'] === 'string' ? value['email'] : undefined;
    const fullName = typeof value['fullName'] === 'string' ? value['fullName'] : undefined;
    const active = typeof value['active'] === 'boolean' ? value['active'] : undefined;
    const status = typeof value['status'] === 'string' ? value['status'] : undefined;

    let businessUnitIds: string[] | undefined;
    if (Array.isArray(value['businessUnitIds'])) {
      businessUnitIds = value['businessUnitIds']
        .filter((entry): entry is string | number => typeof entry === 'string' || typeof entry === 'number')
        .map((entry: string | number) => String(entry));
    }

    return {
      id: String(idRaw),
      role,
      email,
      fullName,
      active,
      status,
      businessUnitIds
    };
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
  }
}
