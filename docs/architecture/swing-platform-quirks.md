# Swing platform quirks

Known platform/compositor bugs that the Swing client works around in
code, with the rationale recorded once so future readers don't relitigate
them. Add to this file when a workaround is layered in (don't bury it
only in a code comment).

---

## Filter popup position — WSLg / XWayland input-region leak

**Symptom (user-visible).** On WSL2 + WSLg, clicking the resource or
reservation **Filter** button (the one labeled "filter" with a 'v'
arrow) shows the filter dropdown, but then a click on the same button to
close it doesn't reach the button. Moving the parent window a bit makes
the button clickable again. On some sessions the popup itself initially
renders **behind** the main window.

**Root cause.** Two layered platform bugs:

1. **Z-order race.** WSLg's compositor (Weston RAIL/RDP backend) sometimes
   stacks heavyweight transient windows below their owner. Tracked in
   [microsoft/wslg#594](https://github.com/microsoft/wslg/issues/594),
   [#801](https://github.com/microsoft/wslg/issues/801).
2. **Oversized hit-region.** XWayland on WSLg sets the popup surface's
   *input region* from the full buffer rect, not from the JDK's X11
   `ShapeInput` calls. So the JWindow's hit-test rectangle extends
   ~20 px above the painted top edge and ~40+ px past the painted left
   edge — into the invisible "shadow buffer" — and the popup window
   swallows clicks that visually land on the button below or beside it.
   Closely related to [JDK-8055834](https://bugs.openjdk.org/browse/JDK-8055834)
   ("Window.setShape does not pass mouse events through") and
   [microsoft/wslg#914](https://github.com/microsoft/wslg/issues/914).

`Window.setShape(rect)` and `_JAVA_AWT_WM_NONREPARENTING=1` were
considered; per JDK-8055834 the shape call is a no-op on Linux for the
input region, and the env var only addresses placement, not hit-test.

**Workaround in code.** All in
[`FilterEditButton.java`](../../rapla-client/src/main/java/org/rapla/client/swing/internal/FilterEditButton.java):

| Change | Why |
|---|---|
| `popup.toFront()` after `setVisible(true)` | Forces restack so the JWindow ends up above its owner under WSLg. |
| Position popup at `(button.x + button.getWidth(), button.y)` — to the **right** of the button, top edges aligned — instead of below it | The button's right edge falls under the popup's invisible left margin only over its rightmost few pixels; the rest of the button stays clickable. Putting the popup *below* the button (the natural dropdown layout) is what the input-region leak destroys, because the leak above the popup is ~20 px and covers the whole button. |
| Use `filterButton.getHeight()` (not the hardcoded `+18`) | The original code assumed the button was 18 px tall; layout often makes it 22–26 px and that mismatch alone made the popup overlap the bottom of the button on every platform. |
| `windowLostFocus` → `SwingUtilities.invokeLater(dismissPopup)` | Click outside the popup (or focus shift to a different app) closes it. Deferred via `invokeLater` so a click that actually lands on the button (when the input-region bug isn't biting) lets the button's action listener run the toggle first. |
| Escape key bound to `dismissPopup` via the popup's `JComponent.WHEN_IN_FOCUSED_WINDOW` input map | Keyboard escape hatch when the button is unclickable. |

The dismissal handlers (focus-loss + Escape) work on every platform —
they're a UX improvement, not WSLg-specific. The right-of-button
placement is the WSLg-specific cost; on native Linux/macOS/Windows it
just looks slightly unusual but works the same.

**If the bug stops applying.** When the JDK or WSLg ships a fix for the
input-region leak (track JDK-8055834 and the WSLg issues above), revert
to placing the popup *below* the button — that's the conventional
dropdown layout. The dismissal handlers and `toFront()` can stay; they
cost nothing and protect against the next compositor bug.

**Empirical margins on WSLg (Java 21, May 2026).** Measured by stepping
the offset:

- ~20 px invisible margin above the popup's painted top edge.
- ~40 px invisible margin left of the popup's painted left edge.

These are the dimensions to clear when positioning. The right-of-button
choice clears the worst (left) margin by `button.width` and side-steps
the top margin entirely.

---

## See also

- [mvp-pattern.md](mvp-pattern.md) — Swing carve-out pattern and headless
  testing. Filter popup logic itself is a UI-rendering concern, not a
  carve-out candidate — the placement decision is platform-bound and
  has no Angular equivalent.
- [AGENTS.md §9](../../AGENTS.md) — Swing client launch lifecycle
  (relevant when reproducing the bug).
