/**
 * Angular dev-server proxy. Forwards REST / OAuth2 / legacy iCal+calendar
 * paths to Spring on :8051; lets ng serve handle /app/** itself.
 *
 * Workflow (PRD 026 §dev / PRD 031 §dev workflow):
 *   Terminal A: cd rapla-angular && npm start
 *   Terminal B: mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false
 *
 * Open http://localhost:4200/app/ — same path as prod (http://host:8051/app/).
 *
 * The bypass + onProxyRes hooks rewrite absolute Location/Origin URLs that
 * Spring AS emits (it builds them from request.getServerPort()=8051) so the
 * browser stays pinned to :4200 throughout the OAuth Code+PKCE round trip.
 */
const TARGET = 'http://localhost:8051';
const PROXIED_ORIGIN = 'http://localhost:4200';

function rewriteHeader(value) {
  if (typeof value !== 'string') return value;
  return value.split(TARGET).join(PROXIED_ORIGIN);
}

module.exports = [
  {
    context: [
      '/api',
      '/oauth2',
      '/.well-known',
      '/userinfo',         // OIDC userinfo (referenced from discovery doc)
      '/connect',          // OIDC end-session ("/connect/logout")
      '/swagger-ui',
      '/v3',
      '/rapla',            // legacy iCal / calendar load-bearing URLs
      '/raplaclient',
      '/raplaclient.jnlp',
      '/webclient',
      '/login',
      '/logout',           // Spring form-login logout
      '/error',
      '/server',
      '/index',
    ],
    target: TARGET,
    secure: false,
    changeOrigin: true,
    xfwd: true,            // sets X-Forwarded-{For,Port,Proto} — http-proxy doesn't set Host
    cookieDomainRewrite: 'localhost',
    logLevel: 'warn',
    onProxyReq(proxyReq) {
      // http-proxy's xfwd doesn't add X-Forwarded-Host. Without it, Spring's
      // forward-headers-strategy can't compute the original public origin —
      // OAuthConfigController returns :8051 URLs instead of :4200, breaking
      // the proxy. Set explicitly.
      proxyReq.setHeader('X-Forwarded-Host', 'localhost:4200');
      proxyReq.setHeader('X-Forwarded-Proto', 'http');
      proxyReq.setHeader('X-Forwarded-Port', '4200');
    },
    onProxyRes(proxyRes) {
      // Rewrite redirect targets so the browser stays on :4200 — Spring AS
      // emits absolute http://localhost:8051/... URLs from getServerPort(),
      // which would otherwise pop the browser out of the proxy.
      if (proxyRes.headers['location']) {
        proxyRes.headers['location'] = rewriteHeader(proxyRes.headers['location']);
      }
      // Same for Content-Security-Policy / Set-Cookie domains if they leak the
      // upstream host. Safe to apply blindly — TARGET is never legitimately in
      // a header sent back to the SPA.
      if (proxyRes.headers['content-security-policy']) {
        proxyRes.headers['content-security-policy'] = rewriteHeader(
          proxyRes.headers['content-security-policy']
        );
      }
    },
  },
];
