import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export type ShopItem = {
  id: string;
  shopCode?: string;
  name?: string;
  countryCode?: string;
  region?: string;
  businessUnitId?: string;
  active?: boolean;
};

export type ShopPayload = {
  shopCode: string;
  name: string;
  countryCode: string;
  region: string;
  businessUnitId: number;
  isActive?: boolean;
};

@Injectable({ providedIn: 'root' })
export class ShopsService {
  constructor(private readonly http: HttpClient) {}

  listShops(): Observable<ShopItem[]> {
    return this.http.get<unknown>('/api/shops').pipe(map((response: unknown) => this.extractShops(response)));
  }

  createShop(payload: ShopPayload): Observable<unknown> {
    return this.http.post('/api/shops', payload);
  }

  updateShop(id: string, payload: ShopPayload): Observable<unknown> {
    return this.http.put(`/api/shops/${encodeURIComponent(id)}`, payload);
  }

  private extractShops(response: unknown): ShopItem[] {
    if (Array.isArray(response)) {
      return response
        .map((entry: unknown) => this.normalizeShop(entry))
        .filter((entry): entry is ShopItem => entry !== null);
    }

    if (this.isRecord(response)) {
      const candidates = [response['items'], response['data'], response['shops']];
      for (const candidate of candidates) {
        if (Array.isArray(candidate)) {
          return candidate
            .map((entry: unknown) => this.normalizeShop(entry))
            .filter((entry): entry is ShopItem => entry !== null);
        }
      }
    }

    return [];
  }

  private normalizeShop(value: unknown): ShopItem | null {
    if (!this.isRecord(value)) {
      return null;
    }

    const idRaw = value['id'];
    if (typeof idRaw !== 'string' && typeof idRaw !== 'number') {
      return null;
    }

    return {
      id: String(idRaw),
      shopCode: typeof value['shopCode'] === 'string' ? value['shopCode'] : undefined,
      name: typeof value['name'] === 'string' ? value['name'] : undefined,
      countryCode: typeof value['countryCode'] === 'string' ? value['countryCode'] : undefined,
      region: typeof value['region'] === 'string' ? value['region'] : undefined,
      businessUnitId:
        typeof value['businessUnitId'] === 'string' || typeof value['businessUnitId'] === 'number'
          ? String(value['businessUnitId'])
          : undefined,
      active: typeof value['active'] === 'boolean' ? value['active'] : undefined
    };
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
  }
}
