import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  BusinessUnitItem,
  BusinessUnitPayload,
  BusinessUnitsService
} from './business-units.service';
import { MainMenuComponent } from '../../shared/main-menu/main-menu.component';

@Component({
  selector: 'app-business-units',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MainMenuComponent],
  templateUrl: './business-units.component.html'
})
export class BusinessUnitsComponent implements OnInit {
  businessUnits: BusinessUnitItem[] = [];

  listError = '';
  listSuccess = '';
  createError = '';
  createSuccess = '';
  updateError = '';
  updateSuccess = '';
  deleteError = '';
  deleteSuccess = '';

  isLoadingList = false;
  isCreating = false;
  isUpdating = false;
  deletingId = '';

  readonly createForm = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required]],
    name: ['', [Validators.required]],
    description: ['']
  });

  readonly updateForm = this.formBuilder.nonNullable.group({
    id: ['', [Validators.required]],
    code: ['', [Validators.required]],
    name: ['', [Validators.required]],
    description: ['']
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly businessUnitsService: BusinessUnitsService,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadBusinessUnits();
  }

  loadBusinessUnits(): void {
    this.isLoadingList = true;
    this.listError = '';

    this.businessUnitsService.listBusinessUnits().subscribe({
      next: (items: BusinessUnitItem[]) => {
        this.businessUnits = items;
        this.isLoadingList = false;
      },
      error: (error: unknown) => {
        this.isLoadingList = false;
        this.listError = this.mapHttpError(error, 'Caricamento Business Unit fallito.');
      }
    });
  }

  createBusinessUnit(): void {
    this.createError = '';
    this.createSuccess = '';

    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const payload = this.toPayload(this.createForm.getRawValue());
    this.isCreating = true;

    this.businessUnitsService.createBusinessUnit(payload).subscribe({
      next: () => {
        this.isCreating = false;
        this.createSuccess = 'Business Unit creata con successo.';
        this.createForm.reset({ code: '', name: '', description: '' });
        this.loadBusinessUnits();
      },
      error: (error: unknown) => {
        this.isCreating = false;
        this.createError = this.mapHttpError(error, 'Creazione Business Unit fallita.');
      }
    });
  }

  selectForUpdate(item: BusinessUnitItem): void {
    this.updateError = '';
    this.updateSuccess = '';

    this.updateForm.setValue({
      id: item.id,
      code: item.code ?? '',
      name: item.name ?? '',
      description: item.description ?? ''
    });
  }

  updateBusinessUnit(): void {
    this.updateError = '';
    this.updateSuccess = '';

    if (this.updateForm.invalid) {
      this.updateForm.markAllAsTouched();
      return;
    }

    const formValue = this.updateForm.getRawValue();
    const payload = this.toPayload(formValue);
    this.isUpdating = true;

    this.businessUnitsService.updateBusinessUnit(formValue.id, payload).subscribe({
      next: () => {
        this.isUpdating = false;
        this.updateSuccess = 'Business Unit aggiornata con successo.';
        this.loadBusinessUnits();
      },
      error: (error: unknown) => {
        this.isUpdating = false;
        this.updateError = this.mapHttpError(error, 'Aggiornamento Business Unit fallito.');
      }
    });
  }

  deleteBusinessUnit(id: string): void {
    this.deleteError = '';
    this.deleteSuccess = '';
    this.deletingId = id;

    this.businessUnitsService.deleteBusinessUnit(id).subscribe({
      next: () => {
        this.deletingId = '';
        this.deleteSuccess = 'Business Unit eliminata con successo.';
        this.loadBusinessUnits();
      },
      error: (error: unknown) => {
        this.deletingId = '';
        this.deleteError = this.mapDeleteError(error);
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private toPayload(value: {
    code: string;
    name: string;
    description: string;
  }): BusinessUnitPayload {
    const description = value.description.trim();

    return {
      code: value.code.trim(),
      name: value.name.trim(),
      ...(description ? { description } : {})
    };
  }

  private mapDeleteError(error: unknown): string {
    const httpError = this.getHttpLikeError(error);
    if (httpError?.status === 409) {
      return 'Eliminazione non consentita: la Business Unit ha dipendenze attive.';
    }

    return this.mapHttpError(error, 'Eliminazione Business Unit fallita.');
  }

  private mapHttpError(error: unknown, fallback: string): string {
    const httpError = this.getHttpLikeError(error);
    if (httpError) {
      const serverMessage = typeof httpError.error === 'string' ? httpError.error : '';
      return serverMessage || `${fallback} (HTTP ${httpError.status})`;
    }

    return fallback;
  }

  private getHttpLikeError(
    value: unknown
  ): { status: number; error?: unknown } | null {
    if (typeof value !== 'object' || value === null) {
      return null;
    }

    const record = value as Record<string, unknown>;
    const status = record['status'];
    if (typeof status !== 'number') {
      return null;
    }

    return {
      status,
      error: record['error']
    };
  }
}
