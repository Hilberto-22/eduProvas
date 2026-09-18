export interface Session {
  id: string;
  assessment_id: string;
  class_id: string;
  code: string;
  status: string;
  starts_at: string;
  ends_at: string;
  duration_minutes: number;
  max_violations: number;
  violation_action: string;
  title: string;
  class_name: string | null;
}
export interface Monitor {
  student_id: string;
  name: string;
  id: string | null;
  status: string | null;
  deadline: string | null;
  last_seen: string | null;
  finish_reason: string | null;
  violations: number;
  answered: number;
}
export interface SessionForm {
  assessmentId: string;
  classId: string;
  startsAt: string;
  endsAt: string;
  durationMinutes: number;
  maxViolations: number;
  violationAction: string;
}
export const emptySessionForm = (): SessionForm => ({
  assessmentId: "",
  classId: "",
  startsAt: "",
  endsAt: "",
  durationMinutes: 60,
  maxViolations: 3,
  violationAction: "REGISTRAR",
});
