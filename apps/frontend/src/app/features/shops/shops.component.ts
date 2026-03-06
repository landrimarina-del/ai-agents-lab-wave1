import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ShopItem, ShopPayload, ShopsService } from './shops.service';
import { MainMenuComponent } from '../../shared/main-menu/main-menu.component';

@Component({
  selector: 'app-shops',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MainMenuComponent],
  templateUrl: './shops.component.html'
})
export class ShopsComponent implements OnInit {
  shops: ShopItem[] = [];

  listError = '';
  actionError = '';
  actionSuccess = '';

  isLoading = false;
  isCreating = false;
  isUpdating = false;

  readonly createForm = this.formBuilder.nonNullable.group({
    shopCode: ['', [Validators.required]],
    name: ['', [Validators.required]],
    countryCode: ['', [Validators.required]],
    region: ['', [Validators.required]],
    businessUnitId: ['', [Validators.required]]
  });

  readonly updateForm = this.formBuilder.nonNullable.group({
    id: ['', [Validators.required]],
    shopCode: ['', [Validators.required]],
    name: ['', [Validators.required]],
    countryCode: ['', [Validators.required]],
    region: ['', [Validators.required]],
    businessUnitId: ['', [Validators.required]]
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly shopsService: ShopsService,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadShops();
  }

  loadShops(): void {
    this.isLoading = true;
    this.listError = '';

    this.shopsService.listShops().subscribe({
      next: (items: ShopItem[]) => {
        this.shops = items;
        this.isLoading = false;
      },
      error: (error: unknown) => {
        this.isLoading = false;
        this.listError = this.mapHttpError(error, 'Caricamento shops fallito.');
      }
    });
  }

  createShop(): void {
    this.actionError = '';
    this.actionSuccess = '';

    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const payload = this.toPayload(this.createForm.getRawValue());
    this.isCreating = true;

    this.shopsService.createShop(payload).subscribe({
      next: () => {
        this.isCreating = false;
        this.actionSuccess = 'Shop creato con successo.';
        this.createForm.reset({ shopCode: '', name: '', countryCode: '', region: '', businessUnitId: '' });
        this.loadShops();
      },
      error: (error: unknown) => {
        this.isCreating = false;
        this.actionError = this.mapHttpError(error, 'Creazione shop fallita.');
      }
    });
  }

  selectForUpdate(item: ShopItem): void {
    this.updateForm.setValue({
      id: item.id,
      shopCode: item.shopCode ?? '',
      name: item.name ?? '',
      countryCode: item.countryCode ?? '',
      region: item.region ?? '',
      businessUnitId: item.businessUnitId ?? ''
    });
  }

  updateShop(): void {
    this.actionError = '';
    this.actionSuccess = '';

    if (this.updateForm.invalid) {
      this.updateForm.markAllAsTouched();
      return;
    }

    const formValue = this.updateForm.getRawValue();
    const payload = this.toPayload(formValue);
    this.isUpdating = true;

    this.shopsService.updateShop(formValue.id, payload).subscribe({
      next: () => {
        this.isUpdating = false;
        this.actionSuccess = 'Shop aggiornato con successo.';
        this.loadShops();
      },
      error: (error: unknown) => {
        this.isUpdating = false;
        this.actionError = this.mapHttpError(error, 'Aggiornamento shop fallito.');
      }
    });
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private toPayload(value: {
    shopCode: string;
    name: string;
    countryCode: string;
    region: string;
    businessUnitId: string;
  }): ShopPayload {
    return {
      shopCode: value.shopCode.trim(),
      name: value.name.trim(),
      countryCode: value.countryCode.trim().toUpperCase(),
      region: value.region.trim(),
      businessUnitId: Number(value.businessUnitId)
    };
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
