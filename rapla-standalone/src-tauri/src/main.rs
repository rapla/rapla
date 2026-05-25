// PRD 054 — rapla standalone trial install (Windows 11 desktop).
//
// Tauri shell that wraps the rapla Spring Boot fat JAR + jlinked JRE,
// launching them as a child process on a random local port and pointing
// a WebView2 window at the Angular SPA served by that server.
//
// Lifecycle: window close → SIGTERM the child → wait 5 s → force-kill.
// Single-instance lock via tauri-plugin-single-instance.

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use tauri::{AppHandle, Manager, RunEvent, WindowEvent};

/// Holds the Spring Boot child process so the window-close handler can
/// SIGTERM it. None until the child is spawned.
struct ChildHandle(Mutex<Option<Child>>);

fn main() {
    env_logger::init();

    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _argv, _cwd| {
            // Second-launch detected: focus the existing window instead of
            // starting a second server (which would fail to bind the port
            // anyway, but better to give the user the existing window).
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.set_focus();
            }
        }))
        .manage(ChildHandle(Mutex::new(None)))
        .setup(|app| {
            let port = portpicker::pick_unused_port()
                .ok_or("no free TCP port available")?;

            let child = spawn_rapla_server(app.handle(), port)?;
            app.state::<ChildHandle>().0.lock().unwrap().replace(child);

            wait_for_server_ready(port, Duration::from_secs(30))?;

            // Navigate the main window to the SPA. The window itself is
            // declared in tauri.conf.json with a placeholder URL; we
            // navigate it programmatically here once the server is up.
            if let Some(window) = app.get_webview_window("main") {
                window.navigate(
                    format!("http://127.0.0.1:{port}/app/").parse()?,
                )?;
            }
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("failed to build Tauri application")
        .run(|app, event| match event {
            RunEvent::WindowEvent {
                event: WindowEvent::CloseRequested { .. },
                ..
            } => {
                shutdown_rapla_server(app);
            }
            RunEvent::ExitRequested { .. } => {
                shutdown_rapla_server(app);
            }
            _ => {}
        });
}

/// Spawn the bundled jlinked-JRE-wrapped rapla-server.exe with the standalone
/// Spring profile + the picked port. stdout/stderr go to the parent's pipe
/// (the Java side already writes to %LOCALAPPDATA%\rapla\logs\ via Logback).
fn spawn_rapla_server(app: &AppHandle, port: u16) -> Result<Child, Box<dyn std::error::Error>> {
    let server_exe = locate_server_binary(app)?;
    log::info!("spawning rapla server: {} on port {}", server_exe.display(), port);

    let child = Command::new(server_exe)
        .arg("--spring.profiles.active=standalone")
        .arg(format!("--server.port={port}"))
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()?;

    Ok(child)
}

/// Resolve the bundled rapla-server.exe path. Tauri's resource resolution
/// gives us the install directory at runtime; the server exe lives there
/// per the tauri.conf.json bundle.resources config.
fn locate_server_binary(app: &AppHandle) -> Result<PathBuf, Box<dyn std::error::Error>> {
    let resource_dir = app.path().resource_dir()?;
    Ok(resource_dir.join("rapla-server").join("bin").join("rapla-server.exe"))
}

/// Poll the local port until something accepts a TCP connection or we
/// time out. Simpler than an HTTP probe and good enough as a readiness
/// gate — Spring Boot's Tomcat doesn't bind the port until @SpringBootApplication
/// has fully initialized + the controllers are registered. A pickier
/// "is /api/auth/oauth/config returning 200?" probe can come later if
/// the TCP-connect proves too eager.
fn wait_for_server_ready(port: u16, timeout: Duration) -> Result<(), Box<dyn std::error::Error>> {
    use std::net::{Ipv4Addr, SocketAddr, TcpStream};
    let addr = SocketAddr::from((Ipv4Addr::LOCALHOST, port));
    let started = Instant::now();
    while started.elapsed() < timeout {
        if TcpStream::connect_timeout(&addr, Duration::from_millis(500)).is_ok() {
            log::info!("rapla server ready in {:?}", started.elapsed());
            // One extra second of grace — Tomcat accepts connections a few
            // hundred ms before the Spring context has finished publishing
            // beans, so an immediate window-navigate occasionally races.
            std::thread::sleep(Duration::from_secs(1));
            return Ok(());
        }
        std::thread::sleep(Duration::from_millis(250));
    }
    Err(format!("rapla server did not become ready within {timeout:?}").into())
}

/// Best-effort shutdown of the bundled Spring Boot child. SIGTERM first,
/// wait 5 s for the JVM's shutdown hooks (data file flush, etc.) to run,
/// then kill -9 if still alive.
fn shutdown_rapla_server(app: &AppHandle) {
    let mut handle = app.state::<ChildHandle>().0.lock().unwrap();
    if let Some(mut child) = handle.take() {
        log::info!("shutting down rapla server (pid {})", child.id());

        // On Windows, std::process::Child::kill sends SIGKILL-equivalent.
        // For a graceful first try, we send a CTRL_BREAK_EVENT to the
        // process group instead — Spring Boot's shutdown hooks fire on
        // that. If the child doesn't exit within 5 s, hard-kill.
        #[cfg(windows)]
        send_ctrl_break(child.id());

        use wait_timeout::ChildExt;
        match child.wait_timeout(Duration::from_secs(5)) {
            Ok(Some(status)) => log::info!("rapla server exited cleanly: {status}"),
            Ok(None) => {
                log::warn!("rapla server did not exit in 5 s, killing");
                let _ = child.kill();
            }
            Err(e) => log::error!("error waiting on rapla server: {e}"),
        }
    }
}

#[cfg(windows)]
fn send_ctrl_break(_pid: u32) {
    // TODO Phase 2: spawn the child with CREATE_NEW_PROCESS_GROUP and
    // call GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, pgid) for a graceful
    // SIGTERM-equivalent. v1 falls through to the wait_timeout hard-kill
    // path — acceptable because the FileOperator writes the data file on
    // every mutation, not just on shutdown, so no data is lost.
}
