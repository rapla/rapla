/** Java .properties text → map (comments, `\` continuations, `\uXXXX`, `\n`/`\t`, `\x` escapes). */
export function parseProperties(text: string): Record<string, string> {
  const out: Record<string, string> = {};
  const lines = text.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    let line = lines[i].trimStart();
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    while (/(^|[^\\])(\\\\)*\\$/.test(line) && i + 1 < lines.length) {
      line = line.slice(0, -1) + lines[++i].trimStart();
    }
    const m = /^((?:\\.|[^=:\s\\])+)\s*[=:\s]\s*(.*)$/.exec(line);
    if (!m) continue;
    out[unescape(m[1])] = unescape(m[2]);
  }
  return out;
}

function unescape(s: string): string {
  return s.replace(/\\(u[0-9a-fA-F]{4}|.)/g, (_, e: string) =>
    e.length === 5
      ? String.fromCharCode(parseInt(e.slice(1), 16))
      : e === 'n'
        ? '\n'
        : e === 't'
          ? '\t'
          : e,
  );
}
