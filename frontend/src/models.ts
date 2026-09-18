export interface Page<T> { items: T[]; page: number; size: number; total: number; }
export const emptyPage = <T>(): Page<T> => ({items: [], page: 0, size: 25, total: 0});
export interface SchoolClass { id: string; name: string; teacher_id: string; students: number; }
export interface Assessment { id: string; title: string; teacher_id: string; locked: boolean; }
export interface Student { id: string; name: string; email: string; }
export interface Account extends Student { role: 'ADMIN' | 'PROFESSOR' | 'ALUNO'; }
export interface Alternative { id: string; label: string; position: number; correct?: boolean; }
export interface Question { id: string; assessment_id: string; prompt: string; kind: string; points: number; position: number; alternatives: Alternative[]; }
export interface Session { id: string; assessment_id: string; class_id: string; code: string; status: string; starts_at: string; ends_at: string; duration_minutes: number; max_violations: number; violation_action: string; title: string; class_name: string | null; }
export interface History { id: string; status: string; deadline: string; title: string; }
export interface Monitor { student_id: string; name: string; id: string | null; status: string | null; deadline: string | null; last_seen: string | null; finish_reason: string | null; violations: number; answered: number; }
export interface AnswerInput { alternativeId: string | null; text: string | null; }
export interface Answer { attempt_id: string; question_id: string; alternative_id: string | null; text_value: string | null; score: number | null; feedback: string | null; updated_at: string; position: number; prompt: string; kind: string; }
export interface Occurrence { id: string; attempt_id: string; kind: string; created_at: string; counted: boolean; }
export interface AttemptStatus { id: string; status: string; deadline: string; server_now: string; finish_reason: string | null; violations: number; }
export interface Attempt extends AttemptStatus { session_id: string; student_id: string; started_at: string; submitted_at: string | null; last_seen: string; session: Session; questions: Question[]; answers: Answer[]; occurrences: Occurrence[]; score: {total: number; pending: number}; max_score: number; }
export interface Items { classes: SchoolClass; assessments: Assessment; sessions: Session; users: Account; students: Student; roster: Monitor; history: History; }
export type ListKey = keyof Items;
export type Lists = { [K in ListKey]: Page<Items[K]> };
