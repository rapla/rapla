import { ACT_AS_PARAM, actAsId } from './act-as';

/** The document URL naming this tab's act-as (`GET /api/documents/**` takes `actAs`, PRD 131 D12). */
export function withActAs(url: string): string {
  return `${url}${url.includes('?') ? '&' : '?'}${ACT_AS_PARAM}=${encodeURIComponent(actAsId())}`;
}

/** The one place that opens an `/api` URL in a new window: acts as this tab, `noopener`. */
export function openDocument(url: string): void {
  window.open(withActAs(url), '_blank', 'noopener');
}
