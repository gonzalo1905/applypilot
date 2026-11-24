import { Component } from '@angular/core';

/**
 * Decorative radar: a faint grid, three amber rings and a slowly turning sweep, with a few
 * blips standing for applications in flight. Purely visual (aria-hidden).
 */
@Component({
  selector: 'app-radar',
  template: `
    <div class="radar" aria-hidden="true">
      <div class="rings">
        <span></span>
        <span></span>
        <span></span>
        <i class="sweep"></i>
        <b class="blip b1"></b>
        <b class="blip b2"></b>
        <b class="blip b3"></b>
      </div>
      <div class="readout">
        <span>HDG 047</span>
        <span>ALT FL320</span>
        <span>5 IN FLIGHT</span>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .radar {
      position: relative;
      height: 100%;
      min-height: 420px;
      overflow: hidden;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      background:
        radial-gradient(circle at 50% 50%, rgb(255 138 61 / 10%), transparent 60%),
        repeating-linear-gradient(0deg, transparent 0 39px, var(--grid-line) 39px 40px),
        repeating-linear-gradient(90deg, transparent 0 39px, var(--grid-line) 39px 40px),
        var(--color-surface);
    }

    .rings {
      position: absolute;
      inset: 0;
      display: grid;
      place-items: center;

      span {
        position: absolute;
        border: 1px solid rgb(255 138 61 / 32%);
        border-radius: 50%;
        aspect-ratio: 1;
      }

      span:nth-child(1) {
        width: min(26%, 150px);
      }

      span:nth-child(2) {
        width: min(52%, 300px);
      }

      span:nth-child(3) {
        width: min(78%, 450px);
      }
    }

    .sweep {
      position: absolute;
      top: 50%;
      left: 50%;
      width: min(39%, 225px);
      height: 2px;
      transform-origin: 0 50%;
      background: linear-gradient(90deg, #ff8a3d, transparent);
      box-shadow: 0 0 30px #ff8a3d;
      animation: sweep 9s linear infinite;
    }

    .blip {
      position: absolute;
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #ff8a3d;
      box-shadow: 0 0 0 4px rgb(255 138 61 / 18%);
      animation: pulse 3s ease-in-out infinite;
    }

    .b1 {
      top: 32%;
      left: 60%;
    }

    .b2 {
      top: 61%;
      left: 36%;
      animation-delay: 1s;
    }

    .b3 {
      top: 45%;
      left: 70%;
      animation-delay: 2s;
    }

    .readout {
      position: absolute;
      right: 1.25rem;
      bottom: 1rem;
      left: 1.25rem;
      display: flex;
      justify-content: space-between;
      color: var(--color-text-muted);
      font-family: var(--font-mono);
      font-size: 0.6875rem;
      letter-spacing: 0.08em;
    }

    @keyframes sweep {
      from {
        transform: rotate(-35deg);
      }

      to {
        transform: rotate(325deg);
      }
    }

    @keyframes pulse {
      50% {
        opacity: 0.35;
      }
    }
  `,
})
export class Radar {}
