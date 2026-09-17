package br.edu.avaliacoes.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class SchoolClassRepository extends JdbcRepositorySupport {
    public SchoolClassRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void requireOwned(UUID classId, UUID teacherId) {
        one("SELECT id FROM school_class WHERE id=? AND teacher_id=?", classId, teacherId);
    }

    public List<Map<String, Object>> findByTeacher(UUID teacherId) {
        return rows("SELECT c.*, (SELECT count(*) FROM enrollment e WHERE e.class_id=c.id) AS students " +
                "FROM school_class c WHERE teacher_id=? ORDER BY name", teacherId);
    }

    public List<Map<String, Object>> findStudents(UUID classId) {
        return rows("SELECT u.id,u.name,u.email FROM enrollment e " +
                "JOIN app_user u ON u.id=e.student_id WHERE e.class_id=? ORDER BY u.name", classId);
    }

    public void create(UUID id, String name, UUID teacherId) {
        update("INSERT INTO school_class(id,name,teacher_id) VALUES(?,?,?)", id, name, teacherId);
    }

    public void enroll(UUID classId, UUID studentId) {
        update("INSERT INTO enrollment(class_id,student_id) VALUES(?,?) ON CONFLICT DO NOTHING", classId, studentId);
    }

    public boolean isEnrolled(UUID classId, UUID studentId) {
        return count("SELECT count(*) FROM enrollment WHERE class_id=? AND student_id=?", classId, studentId) > 0;
    }
}
