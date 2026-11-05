// The slider increases sensitivity to the right; RMS thresholds decrease logarithmically.
export const MIN_THRESHOLD = 0.00001;
export const MAX_THRESHOLD = 0.1;
export function validThreshold(value) {return typeof value === 'number' && Number.isFinite(value) && value >= MIN_THRESHOLD && value <= MAX_THRESHOLD;}
export function thresholdFromSlider(value) {return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, .1 * 10 ** (-Number(value) / 25)));}
export function sliderFromThreshold(value) {return Math.max(0, Math.min(100, -25 * Math.log10(value / .1)));}
export function levelPosition(level) {return Math.max(0, Math.min(100, (20 * Math.log10(Math.max(.00001, level)) + 100)));}
