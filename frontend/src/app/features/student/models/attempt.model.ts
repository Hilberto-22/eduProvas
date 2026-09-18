import type { Session } from "../../teaching/models/session.model";
import type { Question } from "../../teaching/models/assessment.model";
export interface History {
  id: string;
  status: string;
  deadline: string;
  title: string;
}
export interface AnswerInput {
  alternativeId: string | null;
  text: string | null;
}
export interface Answer {
  attempt_id: string;
  question_id: string;
  alternative_id: string | null;
  text_value: string | null;
  score: number | null;
  feedback: string | null;
  updated_at: string;
  position: number;
  prompt: string;
  kind: string;
}
export interface Occurrence {
  id: string;
  attempt_id: string;
  kind: string;
  created_at: string;
  counted: boolean;
}
export interface AttemptStatus {
  id: string;
  status: string;
  deadline: string;
  server_now: string;
  finish_reason: string | null;
  violations: number;
}
export interface Attempt extends AttemptStatus {
  session_id: string;
  student_id: string;
  started_at: string;
  submitted_at: string | null;
  last_seen: string;
  session: Session;
  questions: Question[];
  answers: Answer[];
  occurrences: Occurrence[];
  score: { total: number; pending: number };
  max_score: number;
}
