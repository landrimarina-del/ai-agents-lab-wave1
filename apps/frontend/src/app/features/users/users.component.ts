import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { UserItem, UsersService } from './users.service';
import { MainMenuComponent } from '../../shared/main-menu/main-menu.component';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, MainMenuComponent],
  templateUrl: './users.component.html'
})
export class UsersComponent {
  private static readonly countryAliases: Record<string, string> = {
    ITALY: 'IT',
    ITALIA: 'IT'
  };

  readonly roles = ['GLOBAL_ADMIN', 'COUNTRY_MANAGER', 'SYSTEM_ADMIN'];
  availableCountryScopes: { id: string; code: string; name: string }[] = [];
  users: UserItem[] = [];
  businessUnitInputByUserId: Record<string, string> = {};
  businessUnitMessageByUserId: Record<string, string> = {};
  businessUnitErrorByUserId: Record<string, string> = {};

  createSuccess = '';
  createError = '';
  actionSuccess = '';
  actionError = '';
  listError = '';
  listInfo = '';
  healthText = '';
  healthError = '';

  isCreating = false;
  isDeactivating = false;
  isReactivating = false;
  isListLoading = false;
  isHealthLoading = false;
  activeActionUserId = '';
  activeBuUpdateUserId = '';
  isListEndpointUnavailable = false;

  readonly createForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    fullName: ['', [Validators.required]],
    password: ['', [Validators.required]],
    role: ['GLOBAL_ADMIN', [Validators.required]],
    countryScope: this.formBuilder.nonNullable.control<string[]>([])
  });

  readonly targetForm = this.formBuilder.nonNullable.group({
    targetUserId: ['', [Validators.required]]
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly http: HttpClient,
    private readonly usersService: UsersService,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {
    this.loadUsers();
    this.loadCountryScopes();
  }

  get currentUserId(): string {
    return this.authService.getUserId();
  }

  get currentRole(): string {
    return this.authService.getRole() || 'n/d';
  }

  get roleControlValue(): string {
    return this.createForm.controls.role.value;
  }

  get isCountryScopeRequired(): boolean {
    return this.roleControlValue === 'COUNTRY_MANAGER';
  }

  get isSelfTarget(): boolean {
    return this.targetForm.controls.targetUserId.value.trim() === this.currentUserId;
  }

  createUser(): void {
    this.clearCreateMessages();

    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const payload = this.buildCreatePayload();
    if (!payload) {
      return;
    }

    this.isCreating = true;
    this.usersService.createUser(payload).subscribe({
      next: () => {
        this.isCreating = false;
        this.createSuccess = 'Utente creato con successo.';
        this.loadUsers();
      },
      error: (error: unknown) => {
        this.isCreating = false;
        if (error instanceof HttpErrorResponse && error.status === 403) {
          this.createError = 'Permessi insufficienti: effettua login come GLOBAL_ADMIN o SYSTEM_ADMIN.';
          return;
        }
        this.createError = this.mapHttpError(error, 'Creazione utente fallita.');
      }
    });
  }

  deactivate(): void {
    this.runStatusAction('deactivate');
  }

  reactivate(): void {
    this.runStatusAction('reactivate');
  }

  deactivateUser(user: UserItem): void {
    if (!user.id || user.id === this.currentUserId) {
      this.actionError = 'Non puoi disattivare il tuo stesso utente.';
      return;
    }

    this.runStatusAction('deactivate', user.id);
  }

  reactivateUser(user: UserItem): void {
    if (!user.id) {
      return;
    }

    this.runStatusAction('reactivate', user.id);
  }

  saveBusinessUnits(user: UserItem): void {
    const value = this.businessUnitInputByUserId[user.id] ?? '';
    const businessUnitIds = value
      .split(',')
      .map((entry: string) => entry.trim())
      .filter((entry: string) => entry.length > 0);

    this.businessUnitMessageByUserId[user.id] = '';
    this.businessUnitErrorByUserId[user.id] = '';

    this.activeBuUpdateUserId = user.id;
    this.usersService.updateBusinessUnits(user.id, businessUnitIds).subscribe({
      next: () => {
        this.activeBuUpdateUserId = '';
        this.businessUnitMessageByUserId[user.id] = 'Business Unit associate aggiornate.';
      },
      error: (error: unknown) => {
        this.activeBuUpdateUserId = '';
        this.businessUnitErrorByUserId[user.id] = this.mapHttpError(
          error,
          'Aggiornamento Business Unit associate fallito.'
        );
      }
    });
  }

  loadUsers(): void {
    this.isListLoading = true;
    this.listError = '';
    this.listInfo = '';

    this.usersService.listUsers().subscribe({
      next: (items: UserItem[]) => {
        this.isListLoading = false;
        this.users = items;
        this.isListEndpointUnavailable = false;

        for (const user of items) {
          if (this.isCountryManager(user)) {
            this.businessUnitInputByUserId[user.id] = (user.businessUnitIds ?? []).join(', ');
          }
        }
      },
      error: (error: unknown) => {
        this.isListLoading = false;

        if (error instanceof HttpErrorResponse && error.status === 403) {
          this.listError = 'Accesso negato alla lista utenti. Verifica il ruolo o riesegui login admin.';
          return;
        }

        if (error instanceof HttpErrorResponse && (error.status === 404 || error.status === 405)) {
          this.isListEndpointUnavailable = true;
          this.listInfo =
            'Endpoint lista utenti non disponibile. Usa il fallback manuale inserendo ID utente target.';
          return;
        }

        this.listError = this.mapHttpError(error, 'Caricamento lista utenti fallito.');
      }
    });
  }

  checkHealth(): void {
    this.isHealthLoading = true;
    this.healthText = '';
    this.healthError = '';

    this.http.get<Record<string, unknown>>('/api/health').subscribe({
      next: (response: Record<string, unknown>) => {
        this.isHealthLoading = false;
        this.healthText = JSON.stringify(response);
      },
      error: () => {
        this.isHealthLoading = false;
        this.healthError = 'Health check fallito.';
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  onRoleChange(): void {
    if (!this.isCountryScopeRequired) {
      this.createForm.controls.countryScope.setValue([]);
    }
  }

  private loadCountryScopes(): void {
    this.http.get<unknown>('/api/country-scopes').subscribe({
      next: (response: unknown) => {
        if (Array.isArray(response)) {
          this.availableCountryScopes = response
            .map((entry: unknown) => {
              if (typeof entry !== 'object' || entry === null) {
                return null;
              }
              const record = entry as Record<string, unknown>;
              const idRaw = record['id'];
              const codeRaw = record['code'];
              const nameRaw = record['name'];
              if ((typeof idRaw !== 'string' && typeof idRaw !== 'number') || typeof codeRaw !== 'string' || typeof nameRaw !== 'string') {
                return null;
              }
              return { id: String(idRaw), code: codeRaw, name: nameRaw };
            })
            .filter((item): item is { id: string; code: string; name: string } => item !== null);
        }
      },
      error: () => {
        this.availableCountryScopes = [];
      }
    });
  }

  isCountryManager(user: UserItem): boolean {
    return (user.role ?? '').toUpperCase() === 'COUNTRY_MANAGER';
  }

  isUserActive(user: UserItem): boolean {
    if (typeof user.active === 'boolean') {
      return user.active;
    }

    const status = (user.status ?? '').toUpperCase();
    return status !== 'INACTIVE' && status !== 'DISABLED';
  }

  private buildCreatePayload(): {
    email: string;
    fullName: string;
    password: string;
    role: string;
    countryScope?: string[];
  } | null {
    const formValue = this.createForm.getRawValue();
    const countryScopeValues = formValue.countryScope
      .map(value => this.normalizeCountryScopeValue(value))
      .filter(value => value.length > 0);

    if (formValue.role === 'COUNTRY_MANAGER' && countryScopeValues.length === 0) {
      this.createError = 'countryScope è obbligatorio per ruolo COUNTRY_MANAGER.';
      return null;
    }

    return {
      email: formValue.email,
      fullName: formValue.fullName,
      password: formValue.password,
      role: formValue.role,
      ...(countryScopeValues.length > 0 ? { countryScope: countryScopeValues } : {})
    };
  }

  private normalizeCountryScopeValue(value: string): string {
    const normalized = value.trim().toUpperCase();
    if (!normalized) {
      return '';
    }

    if (normalized.length === 2) {
      return normalized;
    }

    return UsersComponent.countryAliases[normalized] ?? normalized;
  }

  private runStatusAction(action: 'deactivate' | 'reactivate', selectedUserId?: string): void {
    this.actionSuccess = '';
    this.actionError = '';

    const fallbackTargetId = this.targetForm.controls.targetUserId.value.trim();
    const targetId = selectedUserId ?? fallbackTargetId;

    if (!selectedUserId) {
      if (this.targetForm.invalid) {
        this.targetForm.markAllAsTouched();
        return;
      }

      if (action === 'deactivate' && this.isSelfTarget) {
        this.actionError = 'Non puoi disattivare il tuo stesso utente.';
        return;
      }
    }

    if (action === 'deactivate' && targetId === this.currentUserId) {
      this.actionError = 'Non puoi disattivare il tuo stesso utente.';
      return;
    }

    if (action === 'deactivate') {
      this.isDeactivating = true;
    } else {
      this.isReactivating = true;
    }
    this.activeActionUserId = targetId;

    const request$ =
      action === 'deactivate'
        ? this.usersService.deactivateUser(targetId)
        : this.usersService.reactivateUser(targetId);

    request$.subscribe({
      next: () => {
        if (action === 'deactivate') {
          this.isDeactivating = false;
          this.actionSuccess = 'Utente disattivato con successo.';
        } else {
          this.isReactivating = false;
          this.actionSuccess = 'Utente riattivato con successo.';
        }
        this.activeActionUserId = '';
        this.loadUsers();
      },
      error: (error: unknown) => {
        if (action === 'deactivate') {
          this.isDeactivating = false;
        } else {
          this.isReactivating = false;
        }
        this.actionError = this.mapHttpError(
          error,
          action === 'deactivate' ? 'Disattivazione fallita.' : 'Riattivazione fallita.'
        );
        this.activeActionUserId = '';
      }
    });
  }

  private clearCreateMessages(): void {
    this.createSuccess = '';
    this.createError = '';
  }

  private mapHttpError(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const serverMessage =
        typeof error.error === 'string'
          ? error.error
          : error.error && typeof error.error === 'object' && 'message' in error.error
            ? String((error.error as Record<string, unknown>)['message'] ?? '')
            : '';
      return serverMessage || `${fallback} (HTTP ${error.status})`;
    }

    return fallback;
  }
}
