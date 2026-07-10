# TLS / HTTPS — PEM SSL bundle

How to serve Rapla over HTTPS directly from the embedded Tomcat, using a PEM
certificate + private key. Spring Boot 4's **SSL bundle** mechanism handles the
keystore conversion in memory — no JKS / PKCS#12 conversion needed, no extra
dependencies.

> **First decide whether you need this.** A reverse proxy (nginx, Apache,
> Traefik, a load balancer) terminating TLS in front of Rapla is the usual
> production setup — see `deployment.md` §"Configuration". Configure TLS on
> Rapla itself only when there is no proxy in front, or when end-to-end TLS
> from proxy to Rapla is required.

## What you need

- A PEM certificate file (full chain: leaf + intermediates). `-----BEGIN
  CERTIFICATE-----` blocks.
- A PEM private key file. **Unencrypted PKCS#8** (`-----BEGIN PRIVATE KEY-----`)
  is the safe choice — parsed natively by Spring Boot's `PemPrivateKeyParser`
  with the stock JDK 21.

Encrypted PEM (`-----BEGIN ENCRYPTED PRIVATE KEY-----`) works with PBES2-AES
(modern OpenSSL default) by setting `keystore.private-key-password` in the
bundle. **Legacy DES / 3DES encryption needs BouncyCastle**, which Rapla does
not ship — convert the key to unencrypted PKCS#8 instead:

```sh
openssl pkcs8 -topk8 -nocrypt -in legacy.key -out server.key
```

A traditional `-----BEGIN RSA PRIVATE KEY-----` (PKCS#1) file also works
unencrypted, but PKCS#8 is the canonical format.

## Configuration

Drop your PEM files next to the JAR (`/opt/rapla/certs/`) and override
`config/application.yml`:

```yaml
server:
  port: 8443
  ssl:
    bundle: server-bundle

spring:
  ssl:
    bundle:
      pem:
        server-bundle:
          keystore:
            certificate: "file:certs/server.crt"
            private-key: "file:certs/server.key"
```

Paths are resolved against the working directory (the install root). Use
`file:/absolute/path/…` for paths outside the install layout, or `classpath:…`
for PEMs baked into a custom jar.

Restart:

```sh
sudo systemctl restart rapla
curl -v https://localhost:8443/   # verify TLS handshake
```

## Install-layout addition

The `/opt/rapla/` layout from `deployment.md` gains one directory:

```
/opt/rapla/
  rapla-2.1-SNAPSHOT.jar
  config/application.yml
  certs/                     ← PEM cert + key, mode 0750, owned root:rapla
    server.crt
    server.key
  data/
  lib/
  logs/
```

**Permissions matter** — the private key must be unreadable to anyone but the
Rapla service user:

```sh
sudo mkdir -p /opt/rapla/certs
sudo chown root:rapla /opt/rapla/certs /opt/rapla/certs/*
sudo chmod 750 /opt/rapla/certs
sudo chmod 640 /opt/rapla/certs/server.crt
sudo chmod 640 /opt/rapla/certs/server.key
```

## Mutual TLS (optional)

Add a `truststore:` entry alongside `keystore:` and require client certs:

```yaml
server:
  ssl:
    bundle: server-bundle
    client-auth: need        # or 'want' for optional client certs

spring:
  ssl:
    bundle:
      pem:
        server-bundle:
          keystore:
            certificate: "file:certs/server.crt"
            private-key: "file:certs/server.key"
          truststore:
            certificate: "file:certs/client-ca.crt"
```

## Hot reload on cert rotation

Spring Boot can re-read the bundle when the PEM files change on disk — useful
for Let's Encrypt / cert-manager rotation without a restart:

```yaml
spring:
  ssl:
    bundle:
      pem:
        server-bundle:
          reload-on-update: true
          keystore: { ... }
```

The watcher polls the file timestamps; rewrite the cert atomically (`mv` from a
sibling temp file) so the watcher never sees a half-written PEM.

## Behind a reverse proxy

If a proxy already terminates TLS, **do not** configure `server.ssl` here —
keep Rapla on plain HTTP and rely on `X-Forwarded-{Proto,Host,Port}`. The
default `server.forward-headers-strategy: native` (Tomcat's `RemoteIpValve`,
which trusts `X-Forwarded-*` only from RFC1918 internal proxies) already handles
them, and `rapla.oauth.public-base-url` then derives correctly from the request.
Do **not** switch this to `FRAMEWORK` — it trusts the forwarded headers from any
caller, letting an external client spoof its origin. See
`deployment.md` §"Keys to review for production".

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `IllegalStateException: Unable to read PEM private key` | Encrypted PEM with a legacy algorithm (DES/3DES). Convert to unencrypted PKCS#8. |
| `Unable to read PEM certificate` | File path wrong, or the cert file contains a private key (split them). |
| Browser shows the cert but not the intermediates | Your `server.crt` only has the leaf. Concatenate leaf + intermediate(s) into one PEM file. |
| `connection refused` on 8443 but 8051 still works | Spring Boot only binds one port by default. Either drop `server.port` (HTTPS only) or add an HTTP-to-HTTPS redirect connector (custom `WebServerFactoryCustomizer`). |
