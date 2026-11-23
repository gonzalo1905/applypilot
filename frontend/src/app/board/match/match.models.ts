/** Types of a match run (see the backend's AgentRunResponse). */

export type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED';

export type AgentName = 'EXTRACTOR' | 'MATCHER' | 'WRITER' | 'REVIEWER';

/** LLM: the AI model. FALLBACK: deterministic code. REUSED: an earlier result. SKIPPED: did not run. */
export type StepSource = 'LLM' | 'FALLBACK' | 'REUSED' | 'SKIPPED';

export type StepReason =
  | 'NOT_CONFIGURED'
  | 'RATE_LIMITED'
  | 'PROVIDER_ERROR'
  | 'INVALID_OUTPUT'
  | 'DAILY_LIMIT_REACHED'
  | 'PREVIOUS_STEP_FAILED';

export interface MatchedSkill {
  skill: string;
  required: boolean;
  /** A quote from the CV, checked by the server to appear in it. */
  evidence: string;
}

export interface MissingSkill {
  skill: string;
  required: boolean;
}

export interface MatchResult {
  /** 0 to 100, computed by the server; required skills weigh double. */
  score: number;
  matchedSkills: MatchedSkill[];
  missingSkills: MissingSkill[];
  summary: string;
  /** "Matches" dropped because their quote was not found in the CV. */
  rejectedEvidence: number;
}

export interface FabricatedClaim {
  claim: string;
  reason: string;
}

export interface Review {
  approved: boolean;
  fabricatedClaims: FabricatedClaim[];
  missingRequirements: string[];
  toneIssues: string[];
  lengthIssue: string | null;
  suggestions: string[];
  wordCount: number;
  /** False when only code checks ran: claims were not compared with the CV. */
  aiChecked: boolean;
}

export interface RunStep {
  order: number;
  agent: AgentName;
  attempt: number;
  source: StepSource;
  reason: StepReason | null;
  model: string | null;
  modelCalls: number;
  toolCalls: number;
  promptTokens: number | null;
  completionTokens: number | null;
  latencyMs: number;
  inputHash: string;
  startedAt: string;
}

export interface AgentRun {
  id: string;
  applicationId: string;
  status: RunStatus;
  currentAgent: AgentName | null;
  errorCode: string | null;
  match: MatchResult | null;
  coverLetter: string | null;
  letterLanguage: string | null;
  review: Review | null;
  revisions: number;
  steps: RunStep[];
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export function isActive(run: AgentRun): boolean {
  return run.status === 'PENDING' || run.status === 'RUNNING';
}

export const AGENT_LABELS: Record<AgentName, string> = {
  EXTRACTOR: 'Extractor',
  MATCHER: 'Matcher',
  WRITER: 'Writer',
  REVIEWER: 'Reviewer',
};

export const SOURCE_LABELS: Record<StepSource, string> = {
  LLM: 'AI',
  FALLBACK: 'Fallback',
  REUSED: 'Reused',
  SKIPPED: 'Skipped',
};

export const REASON_LABELS: Record<StepReason, string> = {
  NOT_CONFIGURED: 'AI is not configured on this server',
  RATE_LIMITED: 'the AI provider is busy right now',
  PROVIDER_ERROR: 'the AI provider could not be reached',
  INVALID_OUTPUT: 'the AI answer did not pass validation',
  DAILY_LIMIT_REACHED: "today's AI calls are used up",
  PREVIOUS_STEP_FAILED: 'an earlier step produced nothing to work on',
};

/** Messages for run failures that happen after the run was accepted. */
export const RUN_ERROR_LABELS: Record<string, string> = {
  CV_REQUIRED: 'Your CV was removed while the run was working. Upload it again in your profile.',
  ANALYSIS_NOT_FOUND: 'The job posting analysis was removed. Analyze the posting again.',
  AI_DAILY_LIMIT_REACHED: "Today's AI calls are used up. Try again tomorrow.",
  INTERRUPTED: 'The server restarted while the run was working. Start it again.',
  AGENT_RUNS_BUSY: 'Too many runs were waiting. Start it again in a minute.',
};
