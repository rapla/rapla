/**
 * PRD 035: After /app/login?returnTo=<key> completes the OAuth flow,
 * navigate to the matching path instead of the default /reservations.
 *
 * Lets external explorers (GraphiQL, etc.) share the SPA's OAuth callback
 * (/app/auth/callback) without registering their own redirect URI in every
 * configured IdP (rapla SAS, Microsoft Entra, DHBW Keycloak, Google, …).
 *
 * Allowlist-only (not a free-form path) so a malicious link
 * `/app/login?returnTo=https://evil/...` can't bounce the user to an
 * attacker-controlled URL after they sign in.
 */
export const LOGIN_RETURN_PATHS = {
  graphiql: '/graphiql',
  swagger: '/swagger-ui',
  scalar: '/scalar',
} as const;

export type LoginReturnKey = keyof typeof LOGIN_RETURN_PATHS;

export function resolveLoginReturnPath(key: string | null | undefined): string | null {
  if (!key) return null;
  return key in LOGIN_RETURN_PATHS ? LOGIN_RETURN_PATHS[key as LoginReturnKey] : null;
}
