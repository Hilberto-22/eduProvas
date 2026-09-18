export interface Assessment {
  id: string;
  title: string;
  teacher_id: string;
  locked: boolean;
}
export interface Alternative {
  id: string;
  label: string;
  position: number;
  correct?: boolean;
}
export interface Question {
  id: string;
  assessment_id: string;
  prompt: string;
  kind: string;
  points: number;
  position: number;
  alternatives: Alternative[];
}
export interface QuestionInput {
  prompt: string;
  kind: string;
  points: number;
  alternatives: { label: string; correct: boolean }[];
}
