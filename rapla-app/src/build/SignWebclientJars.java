import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertPath;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>Run as a single-file source program (JEP 330):
 * {@code java --add-modules jdk.jartool SignWebclientJars.java
 *        <pkcs11.cfg> <certchain.pem> <alias> <pin> <archiveDir>}
 */
public class SignWebclientJars {

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: SignWebclientJars "
                + "<pkcs11.cfg> <certchain.pem> <alias> <pin> <archiveDir>");
            System.exit(2);
        }
        String pkcs11Cfg = args[0];
        String certChainPem = args[1];
        String alias = args[2];
        String pin = args[3];
        Path archiveDir = Paths.get(args[4]);

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
        PrivateKey key = (PrivateKey) ks.getKey(alias, null);
        if (key == null) {
            throw new IllegalStateException("no private key for alias: " + alias);
        }

        CertPath certPath = readCertPath(certChainPem);

        JarSigner signer = new JarSigner.Builder(key, certPath)
            .digestAlgorithm("SHA-256")
            .signatureAlgorithm("SHA256withECDSA", p11)
            .signerName("RAPLA")
            .build();

        List<Path> jars = new ArrayList<>();
        try (var stream = Files.list(archiveDir)) {
            stream.filter(f -> f.getFileName().toString().endsWith(".jar"))
                  .sorted()
                  .forEach(jars::add);
        }
        if (jars.isEmpty()) {
            System.out.println("No jars to sign in " + archiveDir);
            return;
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
            System.out.println("  [" + (++n) + "/" + jars.size() + "] "
                + jar.getFileName());
        }
        System.out.println("Signed " + n + " jar(s) in "
            + (System.currentTimeMillis() - t0) + " ms");
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
