import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  CountryScopeItem,
  CountryScopesService,
  CreateCountryScopePayload,
  UpdateCountryScopePayload
} from './country-scopes.service';
import { MainMenuComponent } from '../../shared/main-menu/main-menu.component';

@Component({
  selector: 'app-country-scopes',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MainMenuComponent],
  templateUrl: './country-scopes.component.html'
})
export class CountryScopesComponent implements OnInit {
  countryScopes: CountryScopeItem[] = [];

  listError = '';
  actionError = '';
  actionSuccess = '';

  isLoading = false;
  isCreating = false;
  isUpdating = false;
  deactivatingId = '';

  readonly createForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(2)]],
    name: ['', [Validators.required]]
  });

  readonly updateForm = this.formBuilder.nonNullable.group({
    id: ['', [Validators.required]],
    code: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(2)]],
    name: ['', [Validators.required]],
    isActive: [true]
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly countryScopesService: CountryScopesService,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadCountryScopes();
  }

  loadCountryScopes(): void {
    this.isLoading = true;
    this.listError = '';

    this.countryScopesService.listCountryScopes(true).subscribe({
      next: (items: CountryScopeItem[]) => {
        this.countryScopes = items;
        this.isLoading = false;
      },
      error: (error: unknown) => {
        this.isLoading = false;
        this.listError = this.mapHttpError(error, 'Caricamento country scopes fallito.');
      }
    });
  }

  createCountryScope(): void {
    this.actionError = '';
    this.actionSuccess = '';

    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const formValue = this.createForm.getRawValue();
    const payload: CreateCountryScopePayload = {
      code: formValue.code.trim().toUpperCase(),
      name: formValue.name.trim()
    };

    this.isCreating = true;
    this.countryScopesService.createCountryScope(payload).subscribe({
      next: () => {
        this.isCreating = false;
        this.actionSuccess = 'Country scope creata con successo.';
        this.createForm.reset({ code: '', name: '' });
        this.loadCountryScopes();
      },
      error: (error: unknown) => {
        this.isCreating = false;
        this.actionError = this.mapHttpError(error, 'Creazione country scope fallita.');
      }
    });
  }

  selectForUpdate(item: CountryScopeItem): void {
    this.updateForm.setValue({
      id: item.id,
      code: item.code,
      name: item.name,
      isActive: item.active
    });
  }

  updateCountryScope(): void {
    this.actionError = '';
    this.actionSuccess = '';

    if (this.updateForm.invalid) {
      this.updateForm.markAllAsTouched();
      return;
    }

    const formValue = this.updateForm.getRawValue();
    const payload: UpdateCountryScopePayload = {
      code: formValue.code.trim().toUpperCase(),
      name: formValue.name.trim(),
      isActive: formValue.isActive
    };

    this.isUpdating = true;
    this.countryScopesService.updateCountryScope(formValue.id, payload).subscribe({
      next: () => {
        this.isUpdating = false;
        this.actionSuccess = 'Country scope aggiornata con successo.';
        this.loadCountryScopes();
      },
      error: (error: unknown) => {
        this.isUpdating = false;
        this.actionError = this.mapHttpError(error, 'Aggiornamento country scope fallito.');
      }
    });
  }

  deactivateCountryScope(id: string): void {
    this.actionError = '';
    this.actionSuccess = '';
    this.deactivatingId = id;

    this.countryScopesService.deactivateCountryScope(id).subscribe({
      next: () => {
        this.deactivatingId = '';
        this.actionSuccess = 'Country scope eliminata logicamente.';
        this.loadCountryScopes();
      },
      error: (error: unknown) => {
        this.deactivatingId = '';
        this.actionError = this.mapHttpError(error, 'Delete logica country scope fallita.');
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private mapHttpError(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null) {
      const record = error as Record<string, unknown>;
      const status = typeof record['status'] === 'number' ? record['status'] : null;
      const payload = record['error'];
      if (payload && typeof payload === 'object') {
        const payloadRecord = payload as Record<string, unknown>;
        if (typeof payloadRecord['message'] === 'string') {
          return payloadRecord['message'];
        }
      }
      return status ? `${fallback} (HTTP ${status})` : fallback;
    }

    return fallback;
  }
}
