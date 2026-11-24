import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Logo } from '../brand/logo';
import { ThemeToggle } from '../theme/theme-toggle';

/** Header shared by the private pages: brand, navigation, the user's name and logout. */
@Component({
  selector: 'app-topbar',
  imports: [RouterLink, RouterLinkActive, Logo, ThemeToggle],
  templateUrl: './topbar.html',
  styleUrl: './topbar.scss',
})
export class Topbar {
  private readonly auth = inject(AuthService);

  protected readonly user = this.auth.user;
  protected readonly initials = computed(() =>
    (this.user()?.fullName ?? '')
      .split(/\s+/)
      .filter((part) => part.length > 0)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join(''),
  );

  protected logout(): void {
    this.auth.logout();
  }
}
