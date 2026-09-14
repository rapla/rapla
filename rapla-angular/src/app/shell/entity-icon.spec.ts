import { describe, it, expect } from 'vitest';
import { entityIcon } from './entity-icon';

describe('entityIcon', () => {
  it('maps the hardcoded rapla type keys (case-insensitive substring)', () => {
    expect(entityIcon('resource', 'Raum')).toBe('meeting_room');
    expect(entityIcon('resource', 'Kurs')).toBe('class');
    expect(entityIcon('resource', 'Studiengang')).toBe('school');
    expect(entityIcon('resource', 'Gebaeude')).toBe('apartment');
    expect(entityIcon('resource', 'gebäude')).toBe('apartment');
    expect(entityIcon('resource', 'Person')).toBe('person');
  });

  it('uses distinct icons for a user account vs a person resource', () => {
    expect(entityIcon('user')).toBe('account_circle'); // login account
    expect(entityIcon('resource', 'Person')).toBe('person'); // person resource
  });

  it('falls back to the kind icon when the type key is unknown', () => {
    expect(entityIcon('event')).toBe('event');
    expect(entityIcon('group')).toBe('folder');
    expect(entityIcon('resource', 'Sonstiges')).toBe('category');
    expect(entityIcon('resource')).toBe('category');
  });
});
