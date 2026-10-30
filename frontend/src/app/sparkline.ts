import { Component, computed, input } from '@angular/core';

const W = 120;
const H = 34;

/** Tiny trend line (no axes) scaled to its own maximum; empty until there are two values. */
@Component({
  selector: 'app-sparkline',
  template: `
    <svg [attr.viewBox]="'0 0 ' + w + ' ' + h" preserveAspectRatio="none" aria-hidden="true">
      @if (line(); as d) {
        <path [attr.d]="d + ' L' + w + ' ' + h + ' L0 ' + h + ' Z'" [attr.fill]="color()" opacity=".12" />
        <path [attr.d]="d" fill="none" [attr.stroke]="color()" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" />
      }
    </svg>
  `,
  styles: `
    :host { display: block; }
    svg { display: block; width: 100%; height: 34px; }
  `,
})
export class Sparkline {
  readonly values = input.required<number[]>();
  readonly color = input('#1a56db');
  protected readonly w = W;
  protected readonly h = H;

  protected readonly line = computed(() => {
    const v = this.values();
    if (v.length < 2) return '';
    const max = Math.max(...v) || 1;
    return v.map((y, i) => `${i ? 'L' : 'M'}${((i * W) / (v.length - 1)).toFixed(1)} ${(H - 3 - (y / max) * (H - 8)).toFixed(1)}`).join(' ');
  });
}
