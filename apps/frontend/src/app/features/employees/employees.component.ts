import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  CreateEmployeePayload,
  EmployeeItem,
  EmployeesService,
  UpdateEmployeePayload
} from './employees.service';
import { MainMenuComponent } from '../../shared/main-menu/main-menu.component';

@Component({
  selector: 'app-employees',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MainMenuComponent],
  templateUrl: './employees.component.html'
})
export class EmployeesComponent implements OnInit {
  employees: EmployeeItem[] = [];

  listError = '';
  actionError = '';
  actionSuccess = '';

  isLoading = false;
  isCreating = false;
  isUpdating = false;
  activeDeactivationId = '';

  readonly createForm = this.formBuilder.nonNullable.group({
    employeeId: ['', [Validators.required]],
    fullName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    shopId: ['', [Validators.required]]
  });

  readonly updateForm = this.formBuilder.nonNullable.group({
    id: ['', [Validators.required]],
    fullName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    shopId: ['', [Validators.required]]
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly employeesService: EmployeesService,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadEmployees();
  }

  loadEmployees(): void {
    this.isLoading = true;
    this.listError = '';

    this.employeesService.listEmployees().subscribe({
      next: (items: EmployeeItem[]) => {
        this.employees = items;
        this.isLoading = false;
      },
      error: (error: unknown) => {
        this.isLoading = false;
        this.listError = this.mapHttpError(error, 'Caricamento employees fallito.');
      }
    });
  }

  createEmployee(): void {
    this.actionError = '';
    this.actionSuccess = '';

    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const formValue = this.createForm.getRawValue();
    const payload: CreateEmployeePayload = {
      employeeId: formValue.employeeId.trim(),
      fullName: formValue.fullName.trim(),
      email: formValue.email.trim(),
      shopId: Number(formValue.shopId)
    };

    this.isCreating = true;
    this.employeesService.createEmployee(payload).subscribe({
      next: () => {
        this.isCreating = false;
        this.actionSuccess = 'Employee creato con successo.';
        this.createForm.reset({ employeeId: '', fullName: '', email: '', shopId: '' });
        this.loadEmployees();
      },
      error: (error: unknown) => {
        this.isCreating = false;
        this.actionError = this.mapHttpError(error, 'Creazione employee fallita.');
      }
    });
  }

  selectForUpdate(item: EmployeeItem): void {
    this.updateForm.setValue({
      id: item.id,
      fullName: item.fullName ?? '',
      email: item.email ?? '',
      shopId: item.shopId ?? ''
    });
  }

  updateEmployee(): void {
    this.actionError = '';
    this.actionSuccess = '';

    if (this.updateForm.invalid) {
      this.updateForm.markAllAsTouched();
      return;
    }

    const formValue = this.updateForm.getRawValue();
    const payload: UpdateEmployeePayload = {
      fullName: formValue.fullName.trim(),
      email: formValue.email.trim(),
      shopId: Number(formValue.shopId)
    };

    this.isUpdating = true;
    this.employeesService.updateEmployee(formValue.id, payload).subscribe({
      next: () => {
        this.isUpdating = false;
        this.actionSuccess = 'Employee aggiornato con successo.';
        this.loadEmployees();
      },
      error: (error: unknown) => {
        this.isUpdating = false;
        this.actionError = this.mapHttpError(error, 'Aggiornamento employee fallito.');
      }
    });
  }

  deactivateEmployee(id: string): void {
    this.actionError = '';
    this.actionSuccess = '';
    this.activeDeactivationId = id;

    this.employeesService.deactivateEmployee(id).subscribe({
      next: () => {
        this.activeDeactivationId = '';
        this.actionSuccess = 'Employee disattivato con successo.';
        this.loadEmployees();
      },
      error: (error: unknown) => {
        this.activeDeactivationId = '';
        this.actionError = this.mapHttpError(error, 'Disattivazione employee fallita.');
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
