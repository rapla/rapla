import { TableSelection } from './table-selection';

describe('TableSelection', () => {
  let sel: TableSelection<string>;
  const rows = ['a', 'b', 'c', 'd', 'e'];

  beforeEach(() => {
    sel = new TableSelection<string>();
    sel.setRows(rows);
  });

  const selected = () => [...sel.selected()].sort();

  describe('pointer — plain / ctrl / shift (Swing/Excel semantics)', () => {
    it('plain click replaces the selection and sets the anchor', () => {
      sel.pointer('b');
      sel.pointer('d');
      expect(selected()).toEqual(['d']);
      expect(sel.active()).toBe('d');
    });

    it('ctrl-click toggles a row into a discontiguous selection', () => {
      sel.pointer('a');
      sel.pointer('c', { ctrl: true });
      sel.pointer('e', { ctrl: true });
      expect(selected()).toEqual(['a', 'c', 'e']);
    });

    it('ctrl-click toggles an already-selected row out', () => {
      sel.pointer('a');
      sel.pointer('c', { ctrl: true });
      sel.pointer('a', { ctrl: true });
      expect(selected()).toEqual(['c']);
    });

    it('shift-click selects the range from the anchor (replacing)', () => {
      sel.pointer('b');
      sel.pointer('d', { shift: true });
      expect(selected()).toEqual(['b', 'c', 'd']);
    });

    it('shift-click range works upward from the anchor', () => {
      sel.pointer('d');
      sel.pointer('a', { shift: true });
      expect(selected()).toEqual(['a', 'b', 'c', 'd']);
    });

    it('shift-click keeps the anchor — a second shift-click re-ranges from it', () => {
      sel.pointer('c');
      sel.pointer('e', { shift: true });
      sel.pointer('a', { shift: true });
      expect(selected()).toEqual(['a', 'b', 'c']);
    });

    it('ctrl+shift-click ADDS the range to the selection', () => {
      sel.pointer('a');
      sel.pointer('d', { ctrl: true });
      sel.pointer('e', { shift: true, ctrl: true });
      expect(selected()).toEqual(['a', 'd', 'e']);
    });

    it('shift-click without an anchor behaves like a plain click', () => {
      sel.pointer('c', { shift: true });
      expect(selected()).toEqual(['c']);
    });

    it('ctrl-click moves the anchor for a following shift-range', () => {
      sel.pointer('a');
      sel.pointer('c', { ctrl: true });
      sel.pointer('e', { shift: true });
      expect(selected()).toEqual(['c', 'd', 'e']);
    });

    it('a pointer on an unknown key is a no-op', () => {
      sel.pointer('a');
      sel.pointer('zzz');
      expect(selected()).toEqual(['a']);
    });
  });

  describe('keyboard navigation', () => {
    it('ArrowDown moves the active row and selects it', () => {
      sel.pointer('a');
      expect(sel.key('ArrowDown')).toBe(true);
      expect(sel.active()).toBe('b');
      expect(selected()).toEqual(['b']);
    });

    it('ArrowUp from nothing lands on the last row; ArrowDown on the first', () => {
      expect(sel.key('ArrowDown')).toBe(true);
      expect(sel.active()).toBe('a');
      sel.clear();
      sel.setRows(rows);
      expect(sel.key('ArrowUp')).toBe(true);
      expect(sel.active()).toBe('e');
    });

    it('arrows clamp at the ends', () => {
      sel.pointer('a');
      sel.key('ArrowUp');
      expect(sel.active()).toBe('a');
      sel.pointer('e');
      sel.key('ArrowDown');
      expect(sel.active()).toBe('e');
    });

    it('Shift+ArrowDown extends the range from the anchor', () => {
      sel.pointer('b');
      sel.key('ArrowDown', { shift: true });
      sel.key('ArrowDown', { shift: true });
      expect(selected()).toEqual(['b', 'c', 'd']);
      expect(sel.active()).toBe('d');
    });

    it('Shift+ArrowUp shrinks the extension back toward the anchor', () => {
      sel.pointer('b');
      sel.key('ArrowDown', { shift: true });
      sel.key('ArrowDown', { shift: true });
      sel.key('ArrowUp', { shift: true });
      expect(selected()).toEqual(['b', 'c']);
    });

    it('Home/End jump; Shift+End extends to the end', () => {
      sel.pointer('c');
      sel.key('Home');
      expect(sel.active()).toBe('a');
      expect(selected()).toEqual(['a']);
      sel.pointer('c');
      sel.key('End', { shift: true });
      expect(selected()).toEqual(['c', 'd', 'e']);
    });

    it('Ctrl+A selects all rows', () => {
      expect(sel.key('a', { ctrl: true })).toBe(true);
      expect(selected()).toEqual([...rows].sort());
    });

    it('plain "a" is not handled', () => {
      expect(sel.key('a')).toBe(false);
    });

    it('Escape clears the selection and reports handled', () => {
      sel.pointer('b');
      expect(sel.key('Escape')).toBe(true);
      expect(selected()).toEqual([]);
    });

    it('Escape with nothing selected is not handled (lets others act)', () => {
      expect(sel.key('Escape')).toBe(false);
    });
  });

  describe('rendered-order changes (sort / re-group)', () => {
    it('selection survives a re-sort; shift-range follows the NEW order', () => {
      sel.pointer('b');
      sel.setRows(['e', 'd', 'c', 'b', 'a']);
      expect(selected()).toEqual(['b']);
      sel.pointer('d', { shift: true });
      expect(selected()).toEqual(['b', 'c', 'd']);
    });

    it('setRows drops selected keys that vanished', () => {
      sel.pointer('a');
      sel.pointer('c', { ctrl: true });
      sel.setRows(['a', 'b']);
      expect(selected()).toEqual(['a']);
    });

    it('setRows resets a vanished active/anchor', () => {
      sel.pointer('c');
      sel.setRows(['a', 'b']);
      expect(sel.active()).toBeNull();
      sel.pointer('b', { shift: true });
      expect(selected()).toEqual(['b']);
    });
  });

  describe('selection mode (touch)', () => {
    it('in selection mode a plain tap toggles instead of replacing', () => {
      sel.enterSelectionMode();
      sel.pointer('a');
      sel.pointer('c');
      expect(selected()).toEqual(['a', 'c']);
      sel.pointer('a');
      expect(selected()).toEqual(['c']);
    });

    it('deselecting the last row exits selection mode', () => {
      sel.enterSelectionMode();
      sel.pointer('a');
      sel.pointer('a');
      expect(sel.selectionMode()).toBe(false);
      sel.pointer('b');
      sel.pointer('d');
      expect(selected()).toEqual(['d']);
    });

    it('Escape exits selection mode and clears', () => {
      sel.enterSelectionMode();
      sel.pointer('a');
      sel.key('Escape');
      expect(sel.selectionMode()).toBe(false);
      expect(selected()).toEqual([]);
    });

    it('clearing all rows via setRows exits selection mode', () => {
      sel.enterSelectionMode();
      sel.pointer('a');
      sel.setRows(['b', 'c']);
      expect(sel.selectionMode()).toBe(false);
    });
  });

  describe('syncSelected (external source of truth — rail chips)', () => {
    it('replaces the selected set, dropping unknown keys', () => {
      sel.pointer('b');
      sel.syncSelected(['a', 'c', 'zzz']);
      expect(selected()).toEqual(['a', 'c']);
    });

    it('keeps the anchor for a following range', () => {
      sel.pointer('b');
      sel.syncSelected(['a']);
      sel.pointer('d', { shift: true });
      expect(selected()).toEqual(['b', 'c', 'd']);
    });
  });

  describe('derived state', () => {
    it('count reflects the selection size', () => {
      expect(sel.count()).toBe(0);
      sel.pointer('a');
      sel.pointer('c', { ctrl: true });
      expect(sel.count()).toBe(2);
    });

    it('selectedKeys returns keys in rendered order', () => {
      sel.pointer('d');
      sel.pointer('a', { ctrl: true });
      expect(sel.selectedKeys()).toEqual(['a', 'd']);
      sel.setRows(['e', 'd', 'c', 'b', 'a']);
      expect(sel.selectedKeys()).toEqual(['d', 'a']);
    });

    it('clear resets selection and anchor', () => {
      sel.pointer('b');
      sel.clear();
      expect(selected()).toEqual([]);
      sel.pointer('d', { shift: true });
      expect(selected()).toEqual(['d']);
    });
  });
});
