/**
 * Language support tiers in BCosmos (spec v2 section 3.1 & Phase 10).
 *
 * Support levels:
 * - Level 3: Parse, line-accurate symbols, findings, full relationship call graph (Java).
 * - Level 2: Parse, line-accurate symbols, findings, AST linting, AI explanation (TypeScript).
 * - Level 1: Parse, basic tokenization / generic text.
 */
export interface LanguageSupport {
  id: string;
  name: string;
  level: number;
  levelLabel: string;
  badgeClass: string;
}

export function getLanguageSupport(language: string | null | undefined): LanguageSupport {
  const norm = (language ?? '').trim().toLowerCase();
  if (norm === 'java') {
    return {
      id: 'java',
      name: 'Java',
      level: 3,
      levelLabel: 'Level 3 · Full Call Graph & Relationships',
      badgeClass: 'badge--lang-l3',
    };
  }
  if (norm === 'typescript' || norm === 'ts') {
    return {
      id: 'typescript',
      name: 'TypeScript',
      level: 2,
      levelLabel: 'Level 2 · Symbols & AST Linting',
      badgeClass: 'badge--lang-l2',
    };
  }
  return {
    id: norm || 'generic',
    name: language || '—',
    level: 1,
    levelLabel: 'Level 1 · Generic Source',
    badgeClass: 'badge--plain',
  };
}
