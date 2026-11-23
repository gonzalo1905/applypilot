import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { WakeUpBanner } from './core/wake-up/wake-up';

@Component({
  imports: [RouterOutlet, WakeUpBanner],
  selector: 'app-root',
  template: '<router-outlet /><app-wake-up-banner />',
})
export class App {}
