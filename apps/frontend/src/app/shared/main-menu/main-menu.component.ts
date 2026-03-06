import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-main-menu',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './main-menu.component.html'
})
export class MainMenuComponent {
  constructor(private readonly authService: AuthService) {}

  get isGlobalAdmin(): boolean {
    return this.authService.getRole() === 'GLOBAL_ADMIN';
  }
}
