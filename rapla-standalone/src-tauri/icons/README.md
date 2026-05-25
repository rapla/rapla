# Icons

Placeholder. Drop the rapla logo `.ico` file here as `icon.ico` (referenced
from `../tauri.conf.json` → `bundle.icon`).

Tauri's `tauri icon` command can generate the full per-OS icon set from
a single source PNG:

```bash
cargo install tauri-cli@^2
cargo tauri icon path/to/source-1024x1024.png
```

For Windows-only builds you only strictly need `icon.ico`. The other
formats (`.png` for Linux, `.icns` for macOS) can come later if/when
multi-OS support lands.
