export function musicSourceLabel(source: string): string {
  const label = source.replace(/MusicClient$/, '').trim();
  return label || source;
}
