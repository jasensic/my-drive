import { Routes } from '@angular/router';
import { authGuard, setupGuard } from './presentation/auth.guard';
import { DevicesPage } from './presentation/devices.page';
import { LibraryPage } from './presentation/library.page';
import { LoginPage } from './presentation/login.page';
import { PlayerPage } from './presentation/player.page';
import { ShellComponent } from './presentation/shell.component';

export const routes: Routes = [
  { path: 'login', component: LoginPage, canActivate: [setupGuard] },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'library' },
      { path: 'library', component: LibraryPage },
      { path: 'player/:id', component: PlayerPage },
      { path: 'devices', component: DevicesPage },
    ],
  },
];
