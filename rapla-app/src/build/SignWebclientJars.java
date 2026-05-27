import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import jdk.security.jarsigner.JarSigner;

/**
 * Batch JAR signer for the JNLP webclient/ set, used by the rapla-app
 * {@code sign-pkcs11} Maven profile.
 *
 * <p>The maven-jarsigner-plugin shells out to the {@code jarsigner} tool once
 * per jar — a fresh JVM and a fresh PKCS#11 login each time. With a YubiKey
 * whose PIV signing slot has touch policy {@code CACHED} (a touch is valid for
 * only 15 seconds), signing ~23 jars that way spills past the cache window and
 * demands several physical touches.
 *
 * <p>This tool instead signs every jar in a single JVM: one SunPKCS11 login,
 * then all signatures back-to-back. They complete well inside the 15s window,
 * so a single touch covers the whole build — matching the Windows behaviour.
 *
 * <p>This tool is YubiKey-only — it lives in the {@code sign-pkcs11} profile
 * and nothing else uses it. That lets it carry operational defaults (signing
 * alias, digest/signature algos, the two directories the JNLP set lives in)
 * so the pom only has to pass the three machine-local inputs.
 *
 * <p>Run as a single-file source program (JEP 330):
 * {@code java --add-modules jdk.jartool SignWebclientJars.java
 *        <pkcs11.cfg> <certchain.pem> <pin> [archiveDir...]}
 *
 * <p>If no {@code archiveDir} args are passed, the tool signs both default
 * locations relative to the JVM's working directory (rapla-app/ when the
 * exec-maven-plugin runs):
 * <ul>
 *   <li>{@code target/webclient/} — every jar the JNLP launcher pulls.
 *       The {@code replace-bootinf-lib-with-signed} antrun step copies the
 *       signed jars from here into the fat JAR's {@code BOOT-INF/lib/}.</li>
 *   <li>{@code target/classes/static/webclient/} — only rxjava + rapla-client.
 *       These are excluded from {@code BOOT-INF/lib/} by spring-boot:repackage's
 *       {@code <excludes>}, so they live as static resources instead.
 *       {@code maven-jar:jar} packages this dir into the JAR; {@code spring-boot:repackage}
 *       moves the contents to {@code BOOT-INF/classes/static/webclient/}.</li>
 * </ul>
 */
public class SignWebclientJars {

    /** YubiKey PIV slot 9A — fixed for the rapla signing setup. */
    private static final String ALIAS = "Certificate for PIV Authentication";
    private static final String DIGEST = "SHA-256";
    private static final String SIGNATURE_ALGO = "SHA256withECDSA";
    private static final String SIGNER_NAME = "RAPLA";

    /** Default archive directories signed when no positional args follow the
     *  pkcs11.cfg/certchain/pin triple. Paths are resolved relative to the
     *  exec-maven-plugin's working directory (rapla-app/ in practice). */
    private static final String[] DEFAULT_ARCHIVE_DIRS = {
        "target/webclient",
        "target/classes/static/webclient",
    };

    /** Cache dir for previously-signed jars + source-shas manifest. Survives
     *  {@code mvn clean} because it lives outside {@code target/}. Gitignored.
     *  Lets server-only or Angular-only rebuilds skip the YubiKey touch by
     *  reusing the cached signed jars when the unsigned inputs are unchanged. */
    private static final Path CACHE_DIR = Paths.get("signed-webclient-cache");
    private static final Path CACHE_MANIFEST = CACHE_DIR.resolve("source-shas.txt");

    /** Force a fresh sign even on cache hit: {@code -Dwebclient.force-sign=true}. */
    private static final String FORCE_SIGN_PROPERTY = "webclient.force-sign";

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: SignWebclientJars "
                + "<pkcs11.cfg> <certchain.pem> <pin> [archiveDir...]");
            System.exit(2);
        }
        String pkcs11Cfg = args[0];
        String certChainPem = args[1];
        String pin = args[2];

        List<Path> archiveDirs = new ArrayList<>();
        if (args.length > 3) {
            for (int i = 3; i < args.length; i++) {
                archiveDirs.add(Paths.get(args[i]));
            }
        } else {
            for (String d : DEFAULT_ARCHIVE_DIRS) {
                archiveDirs.add(Paths.get(d));
            }
        }

        // Configure the SunPKCS11 provider from the cfg file (OpenSC -> YubiKey).
        Provider p11 = Security.getProvider("SunPKCS11");
        if (p11 == null) {
            throw new IllegalStateException("SunPKCS11 provider not available");
        }
        p11 = p11.configure(pkcs11Cfg);
        Security.addProvider(p11);

        // One C_Login for the whole batch (PIN policy ONCE -> no re-prompt).
        KeyStore ks = KeyStore.getInstance("PKCS11", p11);
        ks.load(null, pin.toCharArray());
        PrivateKey key = (PrivateKey) ks.getKey(ALIAS, null);
        if (key == null) {
            throw new IllegalStateException("no private key for alias: " + ALIAS);
        }

        CertPath certPath = readCertPath(certChainPem);

        JarSigner signer = new JarSigner.Builder(key, certPath)
            .digestAlgorithm(DIGEST)
            .signatureAlgorithm(SIGNATURE_ALGO, p11)
            .signerName(SIGNER_NAME)
            .build();

        List<Path> jars = new ArrayList<>();
        for (Path dir : archiveDirs) {
            if (!Files.isDirectory(dir)) {
                System.out.println("Skipping missing archive dir: " + dir);
                continue;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(f -> f.getFileName().toString().endsWith(".jar"))
                      .sorted()
                      .forEach(jars::add);
            }
        }
        if (jars.isEmpty()) {
            System.out.println("No jars to sign in: " + archiveDirs);
            return;
        }

        // Sha the unsigned inputs BEFORE any signing — these become the cache
        // manifest after a successful sign, and feed the cache-hit comparison
        // on the next build. Uses contentSha256 (sorted entry-by-entry content
        // hash) so the fingerprint is stable across rebuilds — bare file sha
        // drifts because ZIP entry timestamps and central-directory layout
        // change every time the jar is repackaged, even when the logical
        // payload is identical.
        Map<String, String> currentShas = new LinkedHashMap<>();
        for (Path jar : jars) {
            currentShas.put(jar.getFileName().toString(), contentSha256(jar));
        }

        boolean forceSign = Boolean.getBoolean(FORCE_SIGN_PROPERTY);
        if (forceSign) {
            System.out.println("Cache: bypassed via -D" + FORCE_SIGN_PROPERTY + "=true");
        } else if (cacheHit(currentShas)) {
            System.out.println();
            System.out.println("================================================================");
            System.out.println("  CACHE HIT — unsigned inputs unchanged since last signed build.");
            System.out.println("  Restoring " + jars.size() + " signed jar(s) from "
                + CACHE_DIR + " (no YubiKey touch required).");
            System.out.println("================================================================");
            restoreFromCache(jars);
            System.out.println("Done.");
            return;
        } else {
            System.out.println("Cache: miss (inputs changed or no cache) — full sign.");
        }

        System.out.println();
        System.out.println("================================================================");
        System.out.println("  PUT YOUR FINGER ON THE YUBIKEY GOLD DISC NOW — AND HOLD IT.");
        System.out.println("  Signing " + jars.size() + " webclient jar(s) starts in:");
        for (int s = 8; s > 0; s--) {
            System.out.println("      " + s + " ...");
            Thread.sleep(1000L);
        }
        System.out.println("  SIGNING NOW — keep holding the YubiKey until it says done.");
        System.out.println("================================================================");

        long t0 = System.currentTimeMillis();
        int n = 0;
        for (Path jar : jars) {
            Path tmp = jar.resolveSibling(jar.getFileName() + ".signed");
            try (ZipFile in = new ZipFile(jar.toFile());
                 OutputStream out = Files.newOutputStream(tmp)) {
                signer.sign(in, out);
            }
            Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  [" + (++n) + "/" + jars.size() + "] " + jar);
        }
        System.out.println("Signed " + n + " jar(s) in "
            + (System.currentTimeMillis() - t0) + " ms");

        writeCache(jars, currentShas);
    }

    /** Returns true if {@link #CACHE_MANIFEST} exists and lists EXACTLY the same
     *  set of (filename, sha256) pairs as {@code currentShas}, AND every named
     *  signed jar exists in {@link #CACHE_DIR}. Any deviation = miss. */
    private static boolean cacheHit(Map<String, String> currentShas) throws Exception {
        if (!Files.exists(CACHE_MANIFEST)) {
            return false;
        }
        Map<String, String> cachedShas = new LinkedHashMap<>();
        for (String line : Files.readAllLines(CACHE_MANIFEST)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length == 2) cachedShas.put(parts[1], parts[0]);
        }
        if (!cachedShas.keySet().equals(currentShas.keySet())) {
            return false;
        }
        for (Map.Entry<String, String> e : currentShas.entrySet()) {
            if (!e.getValue().equals(cachedShas.get(e.getKey()))) {
                return false;
            }
            if (!Files.exists(CACHE_DIR.resolve(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** Cache hit: copy signed jars from cache over the unsigned target jars. */
    private static void restoreFromCache(List<Path> jars) throws Exception {
        for (Path jar : jars) {
            Path cached = CACHE_DIR.resolve(jar.getFileName().toString());
            Files.copy(cached, jar, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Write signed jars + the source-shas manifest into {@link #CACHE_DIR}. */
    private static void writeCache(List<Path> signedJars,
                                   Map<String, String> sourceShas) throws Exception {
        Files.createDirectories(CACHE_DIR);
        for (Path jar : signedJars) {
            Files.copy(jar, CACHE_DIR.resolve(jar.getFileName().toString()),
                       StandardCopyOption.REPLACE_EXISTING);
        }
        List<String> lines = new ArrayList<>();
        lines.add("# Hashes of UNSIGNED webclient jars as of the last successful sign.");
        lines.add("# Format: <sha256>  <jar-filename>");
        lines.add("# Used by SignWebclientJars to decide whether the next build can");
        lines.add("# reuse cached signed jars (cache hit) or must re-sign (miss).");
        sourceShas.forEach((name, sha) -> lines.add(sha + "  " + name));
        Files.write(CACHE_MANIFEST, lines);
        System.out.println("Cache: wrote " + signedJars.size() + " signed jar(s) + manifest to "
            + CACHE_DIR);
    }

    /**
     * Hex-encoded SHA-256 of the jar's <em>logical content</em> — entries
     * sorted by name, each entry contributing (name bytes, NUL, content bytes,
     * NUL) into the digest. Designed to be stable across rebuilds of the
     * exact same source: insensitive to ZIP entry timestamps, central-
     * directory ordering, and a small allowlist of "build-stamped" entries
     * (see {@link #isExcludedFromContentSha}).
     *
     * <p>Bare file SHA drifts across rebuilds because maven-jar-plugin /
     * maven-archiver stamp current-time into ZIP entry headers on every
     * repackage, and rapla-core's resource filter embeds the build minute
     * into {@code RaplaSystemInfo*.properties}. The fingerprint we cache
     * needs to depend only on what's inside the jar that affects runtime
     * behaviour, not on when it was built.
     */
    private static String contentSha256(Path jar) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zf.entries());
            entries.sort(Comparator.comparing(ZipEntry::getName));
            byte[] buf = new byte[8192];
            for (ZipEntry e : entries) {
                String name = e.getName();
                if (isExcludedFromContentSha(name)) {
                    continue;
                }
                md.update(name.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                if (!e.isDirectory()) {
                    try (var in = zf.getInputStream(e)) {
                        int n;
                        while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
                    }
                }
                md.update((byte) 0);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Entries whose presence/content shouldn't bust the signed-webclient cache. */
    private static boolean isExcludedFromContentSha(String name) {
        // Signing artifacts — present on the cached signed jar, absent on the
        // freshly-copied unsigned jar. Skipping these lets the same fingerprint
        // describe pre- and post-sign content equivalently.
        if (name.startsWith("META-INF/")
            && (name.endsWith(".SF") || name.endsWith(".RSA")
                || name.endsWith(".DSA") || name.endsWith(".EC"))) {
            return true;
        }
        // rapla-core's Maven resource filter embeds the build minute into
        // RaplaSystemInfo*.properties (e.g. "rapla.build = 2026-05-27 14:35 GMT+0").
        // Cosmetic — no effect on the running webclient — but flips every minute.
        // Skipping it means a same-source rebuild is still a cache hit.
        if (name.startsWith("org/rapla/RaplaSystemInfo")
            && name.endsWith(".properties")) {
            return true;
        }
        return false;
    }

    /** Reads every PEM CERTIFICATE block from {@code path}, leaf-first, into a CertPath. */
    private static CertPath readCertPath(String path) throws Exception {
        String pem = Files.readString(Paths.get(path));
        Matcher m = Pattern.compile(
            "-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----",
            Pattern.DOTALL).matcher(pem);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<Certificate> certs = new ArrayList<>();
        while (m.find()) {
            certs.add(cf.generateCertificate(
                new ByteArrayInputStream(m.group().getBytes("US-ASCII"))));
        }
        if (certs.isEmpty()) {
            throw new IllegalStateException("no PEM certificates in " + path);
        }
        return cf.generateCertPath(certs);
    }
}
