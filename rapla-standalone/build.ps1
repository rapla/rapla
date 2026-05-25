# PRD 054 — Windows build orchestrator for the rapla standalone MSI.
#
# Full pipeline: builds the standalone fat JAR via Maven, jlinks a stripped
# JRE, stages the runtime, runs `cargo tauri build` to produce the MSI, and
# optionally signs it with the SSL.com YubiKey via signtool.
#
# Prerequisites on the build host (all on PATH):
#   - JDK 21+ (jlink + jpackage)              winget install EclipseAdoptium.Temurin.21.JDK
#   - Apache Maven                            winget install Apache.Maven
#   - Rust toolchain (rustup → stable)        winget install Rustlang.Rustup
#   - Visual Studio 2022 Build Tools          winget install Microsoft.VisualStudio.2022.BuildTools
#                                             (with "Desktop development with C++" workload)
#   - cargo-tauri 2.x                         cargo install tauri-cli --version "^2.0"
#   - For -Sign: YubiKey + SSL.com cert in Windows Cert Store, signtool.exe
#
# Usage (run from rapla-standalone/ on Windows 11):
#   .\build.ps1                # full pipeline, unsigned (SmartScreen will warn on install)
#   .\build.ps1 -Sign          # full pipeline, signed via SSL.com YubiKey
#   .\build.ps1 -SkipMaven     # skip the Maven step (faster iteration if the JAR is already built)
#   .\build.ps1 -SkipJlink     # skip jlink (faster iteration during Tauri/Rust dev)
#
# Output:
#   src-tauri\target\release\bundle\msi\Rapla_2.1.0_x64_en-US.msi  (~110 MB)
#
# Cross-link: docs/prd/054-standalone-windows-installer.md, README.md

[CmdletBinding()]
param(
    [switch]$Sign,
    [switch]$SkipMaven,
    [switch]$SkipJlink,
    [string]$CertThumbprint = $env:RAPLA_SIGN_THUMBPRINT,
    [string]$TimestampUrl = "http://timestamp.sectigo.com"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$repoRoot = (Resolve-Path "..").Path
$jarPath  = "$repoRoot\rapla-app\target\rapla-standalone.jar"

# --- 1. Build the standalone fat JAR (Maven) ---------------------------
if (-not $SkipMaven) {
    Write-Host "[1/5] mvn -pl rapla-app -am package -Pstandalone -DskipTests"
    Push-Location $repoRoot
    try {
        mvn -pl rapla-app -am package -Pstandalone -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
    }
    finally { Pop-Location }
} else {
    Write-Host "[1/5] skip Maven (--SkipMaven)"
}

if (-not (Test-Path $jarPath)) {
    throw "Standalone fat JAR not found at $jarPath. Run without -SkipMaven."
}
Write-Host "  -> $jarPath  ($([math]::Round((Get-Item $jarPath).Length / 1MB, 1)) MB)"

# --- 2. jlink a stripped Windows x64 JRE -------------------------------
$runtimeDir = "runtime"
$jreDir = "$runtimeDir\jre"
if (-not $SkipJlink) {
    if (Test-Path $jreDir) { Remove-Item -Recurse -Force $jreDir }
    New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null

    # Minimal module set for Spring Boot + Tomcat + the rapla server.
    # `jdeps --module-path` can refine this; the list below is "known-good"
    # for the standalone JAR. java.instrument needed by Spring's lazy proxies.
    $modules = @(
        "java.base", "java.desktop", "java.logging", "java.management",
        "java.naming", "java.net.http", "java.security.jgss",
        "java.security.sasl", "java.sql", "java.xml", "java.xml.crypto",
        "jdk.crypto.cryptoki", "jdk.crypto.ec", "jdk.unsupported",
        "jdk.unsupported.desktop", "jdk.localedata", "jdk.zipfs",
        "java.instrument"
    ) -join ","

    Write-Host "[2/5] jlink -> $jreDir"
    jlink `
        --add-modules $modules `
        --strip-debug `
        --no-header-files `
        --no-man-pages `
        --compress=zip-9 `
        --output $jreDir
    if ($LASTEXITCODE -ne 0) { throw "jlink failed" }
} else {
    Write-Host "[2/5] skip jlink (--SkipJlink)"
    if (-not (Test-Path $jreDir)) { throw "$jreDir missing. Run without -SkipJlink." }
}

# --- 3. Stage the server resources (JRE + fat JAR + launcher) ----------
$serverDir = "$runtimeDir\rapla-server"
if (Test-Path $serverDir) { Remove-Item -Recurse -Force $serverDir }
New-Item -ItemType Directory -Force -Path "$serverDir\bin" | Out-Null
New-Item -ItemType Directory -Force -Path "$serverDir\lib" | Out-Null

Copy-Item -Recurse $jreDir "$serverDir\jre"
Copy-Item $jarPath "$serverDir\lib\rapla-standalone.jar"

# v1 launcher: a tiny .bat shim. Phase 2 can replace with a real .exe via
# launch4j or warp-packer (for clean Task Manager display + no console window).
@"
@echo off
"%~dp0..\jre\bin\java.exe" -jar "%~dp0..\lib\rapla-standalone.jar" %*
"@ | Set-Content -Path "$serverDir\bin\rapla-server.bat" -Encoding ASCII

Write-Host "[3/5] runtime staged at $serverDir"

# --- 4. Cargo Tauri build (produces the MSI) ---------------------------
Push-Location src-tauri
try {
    Write-Host "[4/5] cargo tauri build"
    cargo tauri build
    if ($LASTEXITCODE -ne 0) { throw "cargo tauri build failed" }
}
finally { Pop-Location }

$msi = Get-ChildItem -Path src-tauri\target\release\bundle\msi\*.msi `
    | Select-Object -First 1
if (-not $msi) {
    throw "MSI not produced. Check the cargo tauri build output above."
}

# --- 5. Sign the MSI (only if -Sign) -----------------------------------
if ($Sign) {
    if (-not $CertThumbprint) {
        throw "-Sign was passed but no thumbprint. Set `$env:RAPLA_SIGN_THUMBPRINT or pass -CertThumbprint."
    }
    Write-Host "[5/5] signtool sign  (cert thumbprint: $CertThumbprint)"
    Write-Host "      8-second countdown - get ready to touch the YubiKey..."
    & signtool sign `
        /tr $TimestampUrl `
        /td sha256 `
        /fd sha256 `
        /sha1 $CertThumbprint `
        $msi.FullName
    if ($LASTEXITCODE -ne 0) { throw "signtool sign failed" }
    Write-Host "      verify: signtool verify /pa $($msi.FullName)"
} else {
    Write-Host "[5/5] skip signing (no -Sign flag)"
    Write-Host "      Install will trigger SmartScreen 'Windows protected your PC' -"
    Write-Host "      click 'More info' -> 'Run anyway'. This is fine for testing."
}

Write-Host ""
Write-Host "Build complete:"
Write-Host "  $($msi.FullName)"
Write-Host "  $([math]::Round($msi.Length / 1MB, 1)) MB"
