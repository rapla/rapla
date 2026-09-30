import { parseProperties } from './properties';

describe('parseProperties', () => {
  it('reads keys, skips comments and joins continuation lines', () => {
    const p = parseProperties(
      '# c\n! c\nsave = Speichern\ninfo.text = a \\\n    b\nx:y\nz\\u00e4 = \\u00fc\\n',
    );
    expect(p).toEqual({ save: 'Speichern', 'info.text': 'a b', x: 'y', zä: 'ü\n' });
  });
});
