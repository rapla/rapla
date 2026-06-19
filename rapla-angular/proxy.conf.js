/**
 * Angular dev-server proxy. Forwards rapla REST + the server-rendered login /
 * OAuth flow + legacy /rapla calendar/ical paths to Spring on :8051; lets
 * ng serve handle /app/** itself.
 *
 * Workflow (PRD 026 §dev / PRD 031 §dev workflow):
 *   Terminal A: cd rapla-angular && npm start
 *   Terminal B: mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false
 *
 * Open http://localhost:4200/app/ — same path as prod (http://host:8051/app/).
 *
 * PRD 072 Phase 4 — cookie-credential (model A). The SPA holds NO token; auth
 * lives in an HttpOnly `access_token` cookie the server sets at /login success.
 * For that cookie to be SAME-ORIGIN with the SPA in the dev split (:4200 ≠
 * :8051), the login + OAuth-callback flow MUST be proxied through :4200 (not hit
 * :8051 directly as the old localStorage piggyback did). So `/login`,
 * `/login/oauth2/**` (the server-side OAuth callback), `/oauth2/**`,
 * `/.well-known/**`, `/userinfo`, `/connect/**` are now proxied. `xfwd` +
 * X-Forwarded-Host/Proto make Spring compute the proxy origin (:4200) for the
 * OAuth `{baseUrl}` redirect URI; `cookieDomainRewrite: 'localhost'` rewrites
 * Set-Cookie Domain so cookies stick to the proxy origin; the location rewrite
 * keeps 302s on :4200.
 *
 * (Upstream external IdPs — Keycloak/Google/MS — are reached SERVER-side via
 * Spring `oauth2Login()`; the browser only ever talks to the rapla origin, so
 * no cross-origin IdP hop happens in the browser anymore.)
 */
const TARGET = 'http://localhost:8051';
const PROXIED_ORIGIN = 'http://localhost:4200';

function rewriteHeader(value) {
  if (typeof value !== 'string') return value;
  return value.split(TARGET).join(PROXIED_ORIGIN);
}

module.exports = [
  {
    // PRD 072 — TEMPORARY dev-only DHBW bridge (NOT in the prod build). DHBW
    // Keycloak only whitelists the legacy /app/auth/callback redirect for
    // localhost (no admin to add the conformant /login/oauth2/code/keycloak).
    // We KEEP Spring's per-provider callback (mix-up-attack defence, OAuth 2.0
    // Security BCP): the keycloak ClientRegistration sends the registered
    // /app/auth/callback, and this rewrites the RETURN onto Spring's real
    // per-provider endpoint. Spring validates `state` (not the request path) and
    // uses the saved redirect_uri (/app/auth/callback) for the token call, so it
    // still matches what DHBW issued the code for. Remove once DHBW registers
    // /login/oauth2/code/keycloak. Gated server-side by
    // rapla.oauth.web.dhbw-legacy-callback.
    context: ['/app/auth/callback'],
    target: TARGET,
    secure: false,
    changeOrigin: true,
    xfwd: true,
    logLevel: 'warn',
    pathRewrite: { '^/app/auth/callback': '/login/oauth2/code/keycloak' },
    onProxyReq(proxyReq) {
      proxyReq.setHeader('X-Forwarded-Host', 'localhost:4200');
      proxyReq.setHeader('X-Forwarded-Proto', 'http');
      proxyReq.setHeader('X-Forwarded-Port', '4200');
    },
    onProxyRes(proxyRes) {
      if (proxyRes.headers['location']) {
        proxyRes.headers['location'] = rewriteHeader(proxyRes.headers['location']);
      }
    },
  },
  {
    context: [
      '/api',
      '/swagger-ui',
      '/v3',
      '/rapla',            // legacy iCal / calendar load-bearing URLs
      '/raplaclient',
      '/raplaclient.jnlp',
      '/webclient',
      '/login',            // PRD 072 — server-rendered login page (chooser +
                           // optional password form) the SPA navigates to when
                           // unauthenticated. Also covers /login/oauth2/code/**,
                           // the server-side OAuth callback — must land on :4200
                           // so the access_token cookie is same-origin.
      '/logout',           // Spring form-login logout (clears cookies + slot)
      '/oauth2',           // Spring Authorization Server (authorize/token/jwks)
      '/.well-known',      // OIDC discovery
      '/userinfo',
      '/connect',          // rapla SAS end-session
      '/error',
      '/server',
      '/index',
      '/graphiql',         // Same-origin GraphiQL in dev (cookie auto-sent).
    ],
    target: TARGET,
    secure: false,
    changeOrigin: true,
    xfwd: true,            // sets X-Forwarded-{For,Port,Proto} — http-proxy doesn't set Host
    cookieDomainRewrite: 'localhost',  // Set-Cookie Domain → proxy origin (same-origin cookie)
    logLevel: 'warn',
    onProxyReq(proxyReq) {
      // http-proxy's xfwd doesn't add X-Forwarded-Host. Without it, Spring's
      // forward-headers-strategy can't compute the original public origin, so
      // the OAuth `{baseUrl}` redirect URI + OAuthConfigController URLs would
      // come back as :8051 instead of :4200. Set explicitly.
      proxyReq.setHeader('X-Forwarded-Host', 'localhost:4200');
      proxyReq.setHeader('X-Forwarded-Proto', 'http');
      proxyReq.setHeader('X-Forwarded-Port', '4200');
    },
    onProxyRes(proxyRes) {
      // Rewrite redirect targets so the browser stays on :4200 — Spring emits
      // absolute http://localhost:8051/... URLs from getServerPort() (e.g. the
      // login-success 302 to /app/, the OAuth authorize redirect), which would
      // otherwise pop the browser out of the proxy.
      if (proxyRes.headers['location']) {
        proxyRes.headers['location'] = rewriteHeader(proxyRes.headers['location']);
      }
      if (proxyRes.headers['content-security-policy']) {
        proxyRes.headers['content-security-policy'] = rewriteHeader(
          proxyRes.headers['content-security-policy']
        );
      }
    },
  },
];
