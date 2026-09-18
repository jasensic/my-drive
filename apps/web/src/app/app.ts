import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeModeService } from './presentation/theme.service';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: `<router-outlet />`,
})
export class App {
  constructor(_theme: ThemeModeService) {}
}
