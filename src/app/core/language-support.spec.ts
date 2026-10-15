import { describe, expect, it } from 'vitest';
import { getLanguageSupport } from './language-support';

describe('getLanguageSupport', () => {
  it('returns level 3 full call graph for java', () => {
    const java = getLanguageSupport('java');
    expect(java.level).toBe(3);
    expect(java.name).toBe('Java');
    expect(java.badgeClass).toBe('badge--lang-l3');

    const javaUpper = getLanguageSupport('Java');
    expect(javaUpper.level).toBe(3);
  });

  it('returns level 2 symbols and linting for typescript', () => {
    const ts = getLanguageSupport('typescript');
    expect(ts.level).toBe(2);
    expect(ts.name).toBe('TypeScript');
    expect(ts.badgeClass).toBe('badge--lang-l2');

    const tsShort = getLanguageSupport('ts');
    expect(tsShort.level).toBe(2);
  });

  it('handles unknown or empty language gracefully', () => {
    const empty = getLanguageSupport(null);
    expect(empty.level).toBe(1);
    expect(empty.name).toBe('—');

    const python = getLanguageSupport('python');
    expect(python.level).toBe(1);
    expect(python.name).toBe('python');
  });
});
