import { Component, Inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideDynamicIcon } from '@lucide/angular';
import { Card } from 'primeng/card';
import { FINISH_SPOTIFY_LOGIN } from '../application/use-cases.tokens';
import type { FinishSpotifyLogin } from '../application/use-cases.tokens';
import { extractError } from './login.page';

@Component({
  selector: 'app-spotify-callback-page',
  imports: [Card, RouterLink, LucideDynamicIcon],
  template: `
    <div class="callback-wrap">
      <p-card header="Spotify">
        @if (error()) {
          <div class="stack-form">
            <p class="banner error">{{ error() }}</p>
            <a routerLink="/imports">
              <svg lucideIcon="arrow-left" [size]="16" aria-hidden="true" />
              Back to imports
            </a>
          </div>
        } @else {
          <p>{{ message() }}</p>
        }
      </p-card>
    </div>
  `,
})
export class SpotifyCallbackPage implements OnInit {
  message = signal('Finishing Spotify login…');
  error = signal<string | null>(null);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    @Inject(FINISH_SPOTIFY_LOGIN) private readonly finishLogin: FinishSpotifyLogin,
  ) {}

  ngOnInit() {
    void this.complete();
  }

  private async complete() {
    const params = this.route.snapshot.queryParamMap;
    const oauthError = params.get('error');
    if (oauthError) {
      this.error.set(`Spotify authorization failed: ${oauthError}`);
      return;
    }
    const code = params.get('code');
    const state = params.get('state');
    if (!code || !state) {
      this.error.set('Missing Spotify authorization code.');
      return;
    }
    try {
      await this.finishLogin.execute(code, state);
      this.message.set('Connected. Redirecting…');
      await this.router.navigateByUrl('/imports');
    } catch (err) {
      this.error.set(extractError(err));
    }
  }
}
