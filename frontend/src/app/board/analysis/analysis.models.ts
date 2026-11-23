import { WorkMode } from '../application.models';

export type Seniority = 'INTERN' | 'JUNIOR' | 'MID' | 'SENIOR' | 'LEAD' | 'UNSPECIFIED';

/** LLM: the AI model produced the result. FALLBACK: the keyword extractor did. */
export type AnalysisSource = 'LLM' | 'FALLBACK';

export type FallbackReason =
  'NOT_CONFIGURED' | 'RATE_LIMITED' | 'PROVIDER_ERROR' | 'INVALID_OUTPUT';

/** Structured data extracted from an application's job posting (see the backend's AnalysisResponse). */
export interface JobAnalysis {
  id: string;
  applicationId: string;
  postingText: string;
  company: string | null;
  roleTitle: string | null;
  seniority: Seniority;
  requiredSkills: string[];
  niceToHaveSkills: string[];
  workMode: WorkMode | null;
  location: string | null;
  languages: string[];
  salary: string | null;
  summary: string;
  source: AnalysisSource;
  /** Why the keyword fallback was used; null for AI results. */
  fallbackReason: FallbackReason | null;
  model: string | null;
  /** True when an earlier AI result for the same text was reused. */
  cached: boolean;
  analyzedAt: string;
  /** Model calls the stored result cost: 0 for a fallback or a reused result. */
  modelCalls: number;
  promptTokens: number | null;
  completionTokens: number | null;
}

/** Mirrors the backend limits on the pasted text. */
export const POSTING_MIN_LENGTH = 30;
export const POSTING_MAX_LENGTH = 20_000;

export const SENIORITY_LABELS: Record<Seniority, string> = {
  INTERN: 'Intern',
  JUNIOR: 'Junior',
  MID: 'Mid-level',
  SENIOR: 'Senior',
  LEAD: 'Lead',
  UNSPECIFIED: 'Not specified',
};

export const FALLBACK_REASON_LABELS: Record<FallbackReason, string> = {
  NOT_CONFIGURED: 'AI is not configured on this server',
  RATE_LIMITED: 'the AI provider is busy right now',
  PROVIDER_ERROR: 'the AI provider could not be reached',
  INVALID_OUTPUT: 'the AI answer did not pass validation',
};
