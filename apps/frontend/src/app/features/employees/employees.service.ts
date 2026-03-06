import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export type EmployeeItem = {
  id: string;
  employeeId?: string;
  fullName?: string;
  email?: string;
  shopId?: string;
  shopCode?: string;
  countryCode?: string;
  active?: boolean;
};

export type CreateEmployeePayload = {
  employeeId: string;
  fullName: string;
  email: string;
  shopId: number;
};

export type UpdateEmployeePayload = {
  fullName: string;
  email: string;
  shopId: number;
};

@Injectable({ providedIn: 'root' })
export class EmployeesService {
  constructor(private readonly http: HttpClient) {}

  listEmployees(): Observable<EmployeeItem[]> {
    return this.http
      .get<unknown>('/api/employees')
      .pipe(map((response: unknown) => this.extractEmployees(response)));
  }

  createEmployee(payload: CreateEmployeePayload): Observable<unknown> {
    return this.http.post('/api/employees', payload);
  }

  updateEmployee(id: string, payload: UpdateEmployeePayload): Observable<unknown> {
    return this.http.put(`/api/employees/${encodeURIComponent(id)}`, payload);
  }

  deactivateEmployee(id: string): Observable<unknown> {
    return this.http.patch(`/api/employees/${encodeURIComponent(id)}/deactivate`, {});
  }

  private extractEmployees(response: unknown): EmployeeItem[] {
    if (Array.isArray(response)) {
      return response
        .map((entry: unknown) => this.normalizeEmployee(entry))
        .filter((entry): entry is EmployeeItem => entry !== null);
    }

    if (this.isRecord(response)) {
      const candidates = [response['items'], response['data'], response['employees']];
      for (const candidate of candidates) {
        if (Array.isArray(candidate)) {
          return candidate
            .map((entry: unknown) => this.normalizeEmployee(entry))
            .filter((entry): entry is EmployeeItem => entry !== null);
        }
      }
    }

    return [];
  }

  private normalizeEmployee(value: unknown): EmployeeItem | null {
    if (!this.isRecord(value)) {
      return null;
    }

    const idRaw = value['id'] ?? value['employeeDbId'];
    if (typeof idRaw !== 'string' && typeof idRaw !== 'number') {
      return null;
    }

    return {
      id: String(idRaw),
      employeeId: typeof value['employeeId'] === 'string' ? value['employeeId'] : undefined,
      fullName: typeof value['fullName'] === 'string' ? value['fullName'] : undefined,
      email: typeof value['email'] === 'string' ? value['email'] : undefined,
      shopId:
        typeof value['shopId'] === 'string' || typeof value['shopId'] === 'number'
          ? String(value['shopId'])
          : undefined,
      shopCode: typeof value['shopCode'] === 'string' ? value['shopCode'] : undefined,
      countryCode: typeof value['countryCode'] === 'string' ? value['countryCode'] : undefined,
      active: typeof value['active'] === 'boolean' ? value['active'] : undefined
    };
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
  }
}
