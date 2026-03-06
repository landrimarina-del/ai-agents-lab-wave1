import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  isLoading = false;
  errorMessage = '';

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  constructor(
    private readonly formBuilder: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/app/users']);
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    const { email, password } = this.form.getRawValue();

    this.authService.login(email, password).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/app/users']);
      },
      error: (error: unknown) => {
        this.isLoading = false;
        this.errorMessage = this.mapLoginError(error);
      }
    });
  }

  private mapLoginError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Errore inatteso durante il login.';
    }

    switch (error.status) {
      case 401:
        return 'Credenziali non valide.';
      case 403:
        return 'Accesso negato per questo utente.';
      case 423:
        return 'Account bloccato. Contatta un amministratore.';
      default:
        return 'Errore di autenticazione. Riprova più tardi.';
    }
  }
}
