/**
 * Angular dev-server proxy. Forwards rapla REST + legacy /rapla calendar/ical
 * paths to Spring on :8051; lets ng serve handle /app/** itself.
 *
 * Workflow (PRD 026 §dev / PRD 031 §dev workflow):
 *   Terminal A: cd rapla-angular && npm start
 *   Terminal B: mvn -pl rapla-app -am spring-boot:run -Dspring-boot.run.fork=false
 *
 * Open http://localhost:4200/app/ — same path as prod (http://host:8051/app/).
 *
 * OAuth endpoints (/oauth2/*, /.well-known/*, /userinfo, /connect/*, /login)
 * are intentionally NOT proxied. The SPA hits them directly at :8051 — the
 * same shape any external IdP (Keycloak, Auth0) would need. CORS on the
 * Spring side (AuthorizationServerConfig.corsConfigurationSource) allows
 * cross-origin POST /oauth2/token from :4200. The OAuth library reads
 * absolute :8051 URLs from /api/auth/oauth/config (rapla.oauth.public-base-url
 * in application.yml).
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
      '/swagger-ui',
      '/v3',
      '/rapla',            // legacy iCal / calendar load-bearing URLs
      '/raplaclient',
      '/raplaclient.jnlp',
      '/webclient',
      '/logout',           // Spring form-login logout (sibling of OAuth /login but app-side)
      '/error',
      '/server',
      '/index',
      '/graphiql',         // Same-origin GraphiQL in dev so its
                           // localStorage('access_token') read sees the SPA's
                           // token. Without this entry, GraphiQL on :8051
                           // can't see :4200's localStorage and stays
                           // anonymous. See graphiql/index.html comment block.
                           // (POSTs go to /api/graphql which is already proxied.)
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
      // OAuthConfigController would return :8051 URLs for the app API instead
      // of :4200. Set explicitly.
      proxyReq.setHeader('X-Forwarded-Host', 'localhost:4200');
      proxyReq.setHeader('X-Forwarded-Proto', 'http');
      proxyReq.setHeader('X-Forwarded-Port', '4200');
    },
    onProxyRes(proxyRes) {
      // Rewrite redirect targets so the browser stays on :4200 — Spring emits
      // absolute http://localhost:8051/... URLs from getServerPort(),
      // which would otherwise pop the browser out of the proxy. (OAuth
      // redirects no longer flow through here, but the rapla /api/* paths
      // and legacy /rapla/* paths still can.)
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
