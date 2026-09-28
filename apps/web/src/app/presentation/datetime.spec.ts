import { formatLocalDateTime } from './datetime';

describe('formatLocalDateTime', () => {
  it('renders an empty value as blank', () => {
    expect(formatLocalDateTime(null)).toBe('');
    expect(formatLocalDateTime(undefined)).toBe('');
    expect(formatLocalDateTime('')).toBe('');
  });

  it('keeps unparseable strings', () => {
    expect(formatLocalDateTime('not-a-date')).toBe('not-a-date');
  });

  it('formats a UTC instant in the local zone with Spanish numeric style', () => {
    const iso = '2026-09-28T18:36:00.000Z';
    const expected = new Date(iso).toLocaleString('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    });
    expect(formatLocalDateTime(iso)).toBe(expected);
    expect(formatLocalDateTime(iso)).toMatch(/\d{2}\/\d{2}\/2026/);
    expect(formatLocalDateTime(iso)).not.toContain('T');
    expect(formatLocalDateTime(iso)).not.toMatch(/Z$/);
  });
});
