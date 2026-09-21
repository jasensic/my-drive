import { Routes } from '@angular/router';
import { authGuard, setupGuard } from './presentation/auth.guard';
import { AppUpdatesPage } from './presentation/app-updates.page';
import { DevicesPage } from './presentation/devices.page';
import { ImportsPage } from './presentation/imports.page';
import { LibraryPage } from './presentation/library.page';
import { LoginPage } from './presentation/login.page';
import { PlayerPage } from './presentation/player.page';
import { ShellComponent } from './presentation/shell.component';
import { SpotifyCallbackPage } from './presentation/spotify-callback.page';

export const routes: Routes = [
  { path: 'login', component: LoginPage, canActivate: [setupGuard] },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'photos' },
      { path: 'library', pathMatch: 'full', redirectTo: 'photos' },
      { path: 'music', component: LibraryPage, data: { silo: 'music' } },
      { path: 'photos', component: LibraryPage, data: { silo: 'photos' } },
      { path: 'files', component: LibraryPage, data: { silo: 'files' } },
      { path: 'imports', component: ImportsPage },
      { path: 'imports/spotify/callback', component: SpotifyCallbackPage },
      { path: 'player/:id', component: PlayerPage },
      { path: 'devices', component: DevicesPage },
      { path: 'app-updates', component: AppUpdatesPage },
    ],
  },
];
