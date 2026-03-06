import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { LoginComponent } from './features/login/login.component';
import { BusinessUnitsComponent } from './features/business-units/business-units.component';
import { UsersComponent } from './features/users/users.component';
import { EmployeesComponent } from './features/employees/employees.component';
import { ShopsComponent } from './features/shops/shops.component';
import { CountryScopesComponent } from './features/country-scopes/country-scopes.component';

export const appRoutes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: 'app',
    canActivate: [authGuard],
    children: [
      { path: 'users', component: UsersComponent },
      { path: 'business-units', component: BusinessUnitsComponent },
      { path: 'employees', component: EmployeesComponent },
      { path: 'shops', component: ShopsComponent },
      { path: 'country-scopes', component: CountryScopesComponent },
      { path: '', pathMatch: 'full', redirectTo: 'users' }
    ]
  },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' }
];
