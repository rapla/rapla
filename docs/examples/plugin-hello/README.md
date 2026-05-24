# plugin-hello — minimal drop-in rapla plugin

The smallest possible plugin: one `@AutoConfiguration` + one `@RestController`
+ the autoconfig marker file. Not in the rapla reactor — copy this directory,
change the package + group, build, drop the jar into your rapla `./plugins/`.

See [`../../plugins.md`](../../plugins.md) for the full plugin author guide.

## Layout

```
plugin-hello/
├── pom.xml
└── src/main/
    ├── java/com/exampleplugin/hello/
    │   ├── HelloPluginAutoConfiguration.java
    │   └── HelloController.java
    └── resources/META-INF/spring/
        └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## Build

The plugin POM declares `org.rapla:rapla-server` as `provided`, so your local
Maven must be able to resolve it. Three ways to get that:

| If you're building from… | Make `rapla-server` resolvable via… |
|---|---|
| A real rapla release published to a Maven repo | nothing — it's already on Maven Central / your org repo |
| **The rapla source checkout** (this repo) | `mvn -pl rapla-server -am install -DskipTests` once, to seed `~/.m2/` |
| A snapshot of rapla you built locally | same — `mvn install` the rapla branch you're targeting |

Then build the plugin:

```sh
mvn -f docs/examples/plugin-hello/pom.xml package
```

Produces `docs/examples/plugin-hello/target/rapla-plugin-hello-1.0.jar`.

> Note: AGENTS.md §5 forbids `mvn install` for in-reactor rapla work because
> it shadows the in-tree `target/classes` of sibling modules. That rule
> protects reactor consistency — it does NOT apply to plugin authors who are
> *consumers* of rapla. `mvn install` is the normal way a downstream Maven
> build picks up your rapla version.

## Deploy

```sh
sudo cp target/rapla-plugin-hello-1.0.jar /opt/rapla/plugins/
sudo systemctl restart rapla
curl http://localhost:8051/api/hello
# → hello from the example plugin
```

## Disable without removing

```yaml
# /opt/rapla/config/application.yml
rapla:
  plugins:
    hello:
      enabled: false
```

Restart. `curl /api/hello` returns 404 — the bean wasn't loaded.

## What to change for your own plugin

| File | Change |
|---|---|
| `pom.xml` | `<groupId>`, `<artifactId>`, `<rapla.version>` |
| Java sources | package + class names; map under `/api/<your-prefix>` |
| `AutoConfiguration.imports` | FQCN of your `@AutoConfiguration` class |
| `@ConditionalOnProperty(name = "hello.enabled", ...)` | rename `hello` to your plugin id |

Read [`../../plugins.md`](../../plugins.md) before going further — covers the
package convention, REST `/api/` contract, trust model, and the "no API
stability guarantee" warning that every plugin author needs to internalize.
