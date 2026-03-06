import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export type CountryScopeItem = {
  id: string;
  code: string;
  name: string;
  active: boolean;
};

export type CreateCountryScopePayload = {
  code: string;
  name: string;
};

export type UpdateCountryScopePayload = {
  code: string;
  name: string;
  isActive?: boolean;
};

@Injectable({ providedIn: 'root' })
export class CountryScopesService {
  constructor(private readonly http: HttpClient) {}

  listCountryScopes(includeInactive = false): Observable<CountryScopeItem[]> {
    return this.http
      .get<unknown>(`/api/country-scopes?includeInactive=${includeInactive}`)
      .pipe(map((response: unknown) => this.extractItems(response)));
  }

  createCountryScope(payload: CreateCountryScopePayload): Observable<unknown> {
    return this.http.post('/api/country-scopes', payload);
  }

  updateCountryScope(id: string, payload: UpdateCountryScopePayload): Observable<unknown> {
    return this.http.put(`/api/country-scopes/${encodeURIComponent(id)}`, payload);
  }

  deactivateCountryScope(id: string): Observable<unknown> {
    return this.http.patch(`/api/country-scopes/${encodeURIComponent(id)}/deactivate`, {});
  }

  private extractItems(response: unknown): CountryScopeItem[] {
    if (Array.isArray(response)) {
      return response
        .map((entry: unknown) => this.normalizeItem(entry))
        .filter((entry): entry is CountryScopeItem => entry !== null);
    }

    if (typeof response === 'object' && response !== null) {
      const record = response as Record<string, unknown>;
      const candidates = [record['items'], record['data'], record['countries']];
      for (const candidate of candidates) {
        if (Array.isArray(candidate)) {
          return candidate
            .map((entry: unknown) => this.normalizeItem(entry))
            .filter((entry): entry is CountryScopeItem => entry !== null);
        }
      }
    }

    return [];
  }

  private normalizeItem(value: unknown): CountryScopeItem | null {
    if (typeof value !== 'object' || value === null) {
      return null;
    }

    const record = value as Record<string, unknown>;
    const idRaw = record['id'];
    const codeRaw = record['code'];
    const nameRaw = record['name'];

    if ((typeof idRaw !== 'string' && typeof idRaw !== 'number') || typeof codeRaw !== 'string' || typeof nameRaw !== 'string') {
      return null;
    }

    return {
      id: String(idRaw),
      code: codeRaw,
      name: nameRaw,
      active: typeof record['active'] === 'boolean' ? record['active'] : true
    };
  }
}
