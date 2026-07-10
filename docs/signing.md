# Code signing — JNLP webclient jars

The JNLP webclient (`<all-permissions/>` Java Web Start) requires **every** jar in
the launch set to be signed by a consistent identity, or the launcher aborts. The
`rapla-app` build signs the staged `target/webclient/*.jar` set during
`prepare-package`, before they are bundled into the Spring Boot fat JAR.

Two signing profiles exist in `rapla-app/pom.xml`:

| Profile | Identity | Who uses it |
|---|---|---|
| `sign-jks` | self-signed JKS keystore — defaults to `raplaselfsigned.ks` (`keystore.*` props in `rapla-bom`) | developers without the maintainer's hardware token |
| `sign-pkcs11` | **YubiKey** (PIV applet, OpenSC PKCS#11) — the real `erdkante GmbH` SSL.com code-signing certificate | the maintainer, for all rapla / dhbwrapla builds |

**Signing identity is per developer.** The maintainer (Christopher Kohlhaas) signs
every rapla and dhbwrapla build with the `erdkante GmbH` YubiKey via `-Psign-pkcs11`.
That YubiKey is personal hardware — other developers cannot use it, so they sign
with `-Psign-jks` (the self-signed `raplaselfsigned.ks`) or their own keystore.
A developer's keystore choice can be overridden with `-Dkeystore.file=…` or via
`keystore.*` properties in their machine-local `~/.m2/settings.xml`.

This document covers `sign-pkcs11` — the YubiKey signing path, including the
WSL2/Ubuntu setup that makes a USB hardware token usable inside WSL.

```bash
mvn -pl rapla-app -am package -DskipTests -Psign-pkcs11
```

---

## Signing identity

- Hardware token: **YubiKey 5** (OTP+FIDO+CCID), PIV slot `9A` ("PIV Authentication").
- Key: ECCP256. PIN policy `ONCE`, touch policy `CACHED` (a touch is valid 15 s).
- Certificate: `CN=erdkante GmbH` → `SSL.com Code Signing Intermediate CA ECC R2`
  → `SSL.com Root Certification Authority ECC`. Valid until 2028-12-09.

### Machine-local signing inputs — nothing committed

The `sign-pkcs11` profile takes all its inputs from three Maven properties. The
maintainer defines them in their machine-local `~/.m2/settings.xml`; **none are in
the repo and there is no default in the pom** — so nothing machine-specific or
secret is committed, and no `.gitignore` entry is needed.

| Property | Value | What it is |
|---|---|---|
| `pkcs11.cfg` | path to a file in `~/.m2/` | SunPKCS11 config — points Java at the OpenSC PKCS#11 library |
| `pkcs11.certchain` | path to a file in `~/.m2/` | the leaf→intermediate→root chain, embedded into each signature |
| `pkcs11.pin` | the YubiKey PIV PIN | unlocks the token |

The two files live next to `settings.xml`, in `~/.m2/` — outside the repo tree.
Example `~/.m2/settings.xml` (inside the active profile's `<properties>`):

```xml
<pkcs11.cfg>${user.home}/.m2/rapla-pkcs11.cfg</pkcs11.cfg>
<pkcs11.certchain>${user.home}/.m2/rapla-certificate-chain.pem</pkcs11.certchain>
<pkcs11.pin>YOUR-PIV-PIN</pkcs11.pin>
```

`~/.m2/rapla-pkcs11.cfg` on WSL2/Linux:

```
name = OpenSC
library = /usr/lib/x86_64-linux-gnu/opensc-pkcs11.so
slotListIndex = 0
```

(Override per-build with `-Dpkcs11.cfg=…` etc. if needed.)

---

## One-time setup on WSL2 / Ubuntu

A YubiKey is a USB device; WSL2 has no USB by default. Four pieces must be in place.

### 1. USB passthrough — usbipd-win (Windows side)

Install [usbipd-win](https://github.com/dorssel/usbipd-win) on Windows, then share
and attach the YubiKey:

```powershell
winget install --exact dorssel.usbipd-win      # one-time, elevated
usbipd list                                    # find the YubiKey BUSID (VID 1050)
usbipd bind   --busid <BUSID>                   # one-time, elevated
usbipd attach --wsl --busid <BUSID>             # every WSL session — see below
```

Attaching the YubiKey to WSL detaches it from Windows. Confirm inside WSL with
`lsusb` — it should list `1050:0407 Yubico.com Yubikey`.

### 2. Ubuntu packages

```bash
sudo apt-get install -y opensc pcscd pcsc-tools yubico-piv-tool yubikey-manager usbutils
```

`opensc` provides the PKCS#11 module and `pkcs11-tool`/`opensc-tool`; `pcscd` is the
smart-card daemon; `yubikey-manager` (`ykman`) reads slot policies.

### 3. Polkit rule for pcscd

pcscd 2.4+ gates client access via polkit (`org.debian.pcsc-lite.access_pcsc`).
WSL2 has no seat/login session, so the default "local active session" check
rejects the user. Grant access unconditionally:

```bash
# /etc/polkit-1/rules.d/49-pcscd-allow.rules
polkit.addRule(function(action, subject) {
    if (action.id == "org.debian.pcsc-lite.access_pcsc" ||
        action.id == "org.debian.pcsc-lite.access_card") {
        return polkit.Result.YES;
    }
});
```

Without this every PC/SC client reports `No smart card readers found` even though
pcscd itself sees the reader.

### 4. OpenSC `atomic` mode

`/etc/opensc/opensc.conf` — keep the C_Login state stable across the multiple
PKCS#11 sessions a Java signer opens. Without it signing fails
`CKR_USER_NOT_LOGGED_IN`:

```
app default {
    pkcs11 {
        atomic = true;
        lock_login = true;
    }
}
```

---

## Per-session: re-attach the YubiKey

usbipd attachments do **not** survive a `wsl --shutdown` or a YubiKey unplug. At the
start of a signing session:

```powershell
usbipd attach --wsl --busid <BUSID>      # on Windows
```

```bash
opensc-tool --list-readers               # in WSL — expect "Yubico YubiKey ... 00 00"
```

If the reader is missing, restart pcscd: `sudo systemctl restart pcscd.socket`.

### Attaching the YubiKey from inside WSL (no PowerShell switch)

`usbipd.exe` is on the Windows PATH and reachable from WSL via the interop layer
— so the attach can be driven from the same bash session that runs the build, no
need to switch to PowerShell. The YubiKey's BUSID is stable for a given USB port
(VID `1050:0407`), so you can hard-code it once you know it.

```bash
usbipd.exe list                                  # find the VID 1050:0407 BUSID — typically 2-2
usbipd.exe attach --wsl --busid 2-2              # forward to WSL (idempotent — safe to re-run)
lsusb | grep -i yubi                             # verify: "1050:0407 Yubico.com Yubikey ..."
opensc-tool --list-readers                       # verify pcscd sees the reader
```

Failure modes:
- `lsusb` empty → attach didn't take effect. Re-run `usbipd.exe attach` (most common after `wsl --shutdown`).
- `lsusb` shows the YubiKey but `opensc-tool` says "No smart card readers found" → `sudo systemctl restart pcscd.socket`.
- Both work but `pkcs11-tool ... --login` returns 0 slots → the polkit rule (step 3) is missing or pcscd is wedged. Restart pcscd; if still 0 slots, detach + re-attach via `usbipd.exe detach --busid 2-2 && usbipd.exe attach --wsl --busid 2-2`.

The `bind` step (one-time, elevated) only has to be done once per device — after
that the device stays in `Shared` state across reboots and only `attach` is needed
per session.

---

## Running a signed build

**Step 0 — always attach the YubiKey first.** Do this *before* the build, every time,
even if you think it's already attached: the attach does not survive a `wsl --shutdown`
or an unplug, and a `Shared`-but-not-`Attached` token makes the signing step fail late
(after a full compile) with `slotListIndex is 0 but token only has 0 slots`. The attach
is idempotent — safe to run when already attached:

```bash
usbipd.exe attach --wsl --busid 2-2              # VID 1050:0407, busid typically 2-2
pkcs11-tool --list-slots | grep -i 'erdkante'    # confirm: token label "erdkante GmbH"
```

Then build:

```bash
mvn -pl rapla-app -am package -DskipTests -Psign-pkcs11
```

Sequence inside the build: compile → stage `webclient/` jars → **sign** → assemble
fat JAR → replace `BOOT-INF/lib/` entries with the signed copies → repackage.

When the signing step starts you will see an **8-second countdown**. Put your finger
on the YubiKey gold disc and **hold it** until "Signed N jar(s)" appears — a single
touch-and-hold covers the whole set.

Verify afterwards:

```bash
for j in rapla-app/target/webclient/*.jar; do jarsigner -verify "$j" | grep verified; done
```

### Why a custom batch signer (not maven-jarsigner-plugin)

The `sign-pkcs11` profile does **not** use `maven-jarsigner-plugin`. That plugin
forks one `jarsigner` process per jar. With the YubiKey's `CACHED` touch policy
(15 s window) signing ~23 jars that way drifts past the cache and demands a fresh
physical touch every few jars.

Instead the profile runs `rapla-app/src/build/SignWebclientJars.java` (a single-file
source program, JEP 330) via `exec-maven-plugin`. It signs the whole set in **one
JVM** — one login, one PKCS#11 session, all signatures back-to-back — using the
`jdk.security.jarsigner.JarSigner` API. One touch covers the build.

`exec-maven-plugin` is pinned + defaulted to `skip=true` in `rapla-bom`; the
`sign-pkcs11` execution re-enables it with `<skip>false</skip>`.

---

## Signing without the YubiKey — `sign-jks`

Developers who don't have the maintainer's YubiKey sign with the self-signed
`raplaselfsigned.ks` keystore. No hardware, no setup, no touch, no `~/.m2/`
properties:

```bash
mvn -pl rapla-app -am package -DskipTests -Psign-jks
```

`sign-jks` uses `maven-jarsigner-plugin` and inherits its keystore defaults from
`rapla-bom` (`keystore.file` → `raplaselfsigned.ks` at the repo root,
`keystore.password` → `raplaselfsigned`). Override with `-Dkeystore.file=…` /
`-Dkeystore.alias=…` / `-Dkeystore.password=…` (or `keystore.*` properties in a
machine-local `~/.m2/settings.xml`) to sign with your own keystore.

Self-signed jars launch under JNLP / OpenWebStart but show an "unknown publisher"
warning — fine for testing, not for distribution. Released builds use
`-Psign-pkcs11` (the maintainer's real `erdkante GmbH` certificate).

A plain `mvn package` with **no** signing profile leaves the webclient jars
unsigned — fine for a normal build, but a JNLP launch will then abort.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `No smart card readers found` | YubiKey not attached (`usbipd attach`), pcscd down, or missing polkit rule (step 3). |
| `CKR_USER_NOT_LOGGED_IN`, fails fast | Missing OpenSC `atomic` mode (step 4); or the card drifted into a bad state — reset it (below). |
| `CKR_USER_NOT_LOGGED_IN`, after a long hang | The signing operation waited for a touch that never came. Touch the YubiKey when its LED blinks. |
| Signing succeeds but `jarsigner -verify` fails | Jars were double-signed (an old signature plus the new one). Restage fresh unsigned jars — `rm -rf rapla-app/target/webclient` then rebuild. |
| `skipping execute as per configuration` | The `exec-maven-plugin` execution is missing `<skip>false</skip>` (inherited `skip=true` from `rapla-bom`). |

**Card reset** — when the token starts rejecting every operation:

```powershell
usbipd detach --busid <BUSID> ; usbipd attach --wsl --busid <BUSID>
```

```bash
sudo systemctl restart pcscd.socket
pkcs11-tool --module /usr/lib/x86_64-linux-gnu/opensc-pkcs11.so --login --pin <PIV-PIN> \
  --sign --mechanism ECDSA-SHA256 --id 01 -i /tmp/t.bin -o /tmp/t.sig   # touch when it blinks
```
