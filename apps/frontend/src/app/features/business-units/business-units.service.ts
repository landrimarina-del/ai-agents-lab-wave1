import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export type BusinessUnitItem = {
  id: string;
  code?: string;
  name?: string;
  description?: string;
  active?: boolean;
};

export type BusinessUnitPayload = {
  code: string;
  name: string;
  description?: string;
};

@Injectable({ providedIn: 'root' })
export class BusinessUnitsService {
  constructor(private readonly http: HttpClient) {}

  listBusinessUnits(): Observable<BusinessUnitItem[]> {
    return this.http
      .get<unknown>('/api/business-units')
      .pipe(map((response: unknown) => this.extractBusinessUnits(response)));
  }

  createBusinessUnit(payload: BusinessUnitPayload): Observable<unknown> {
    return this.http.post('/api/business-units', payload);
  }

  updateBusinessUnit(id: string, payload: BusinessUnitPayload): Observable<unknown> {
    return this.http.put(`/api/business-units/${encodeURIComponent(id)}`, payload);
  }

  deleteBusinessUnit(id: string): Observable<unknown> {
    return this.http.delete(`/api/business-units/${encodeURIComponent(id)}`);
  }

  private extractBusinessUnits(response: unknown): BusinessUnitItem[] {
    if (Array.isArray(response)) {
      return response
        .map((entry: unknown) => this.normalizeBusinessUnit(entry))
        .filter((entry): entry is BusinessUnitItem => entry !== null);
    }

    if (this.isRecord(response)) {
      const candidates = [response['items'], response['data'], response['businessUnits']];
      for (const candidate of candidates) {
        if (Array.isArray(candidate)) {
          return candidate
            .map((entry: unknown) => this.normalizeBusinessUnit(entry))
            .filter((entry): entry is BusinessUnitItem => entry !== null);
        }
      }
    }

    return [];
  }

  private normalizeBusinessUnit(value: unknown): BusinessUnitItem | null {
    if (!this.isRecord(value)) {
      return null;
    }

    const idRaw = value['id'] ?? value['businessUnitId'];
    if (typeof idRaw !== 'string' && typeof idRaw !== 'number') {
      return null;
    }

    return {
      id: String(idRaw),
      code: typeof value['code'] === 'string' ? value['code'] : undefined,
      name: typeof value['name'] === 'string' ? value['name'] : undefined,
      description: typeof value['description'] === 'string' ? value['description'] : undefined,
      active: typeof value['active'] === 'boolean' ? value['active'] : undefined
    };
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
  }
}
