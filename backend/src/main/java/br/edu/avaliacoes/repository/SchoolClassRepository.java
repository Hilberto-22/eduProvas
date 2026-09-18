package br.edu.avaliacoes.repository;

import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;
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

    public PageResponse<Map<String, Object>> findByTeacher(UUID teacherId, PageRequest page) {
        return page("SELECT c.id,c.name,c.teacher_id, (SELECT count(*) FROM enrollment e WHERE e.class_id=c.id) AS students " +
                "FROM school_class c WHERE teacher_id=? ORDER BY name,c.id",
                "SELECT count(*) FROM school_class WHERE teacher_id=?", page, teacherId);
    }

    public PageResponse<Map<String, Object>> findStudents(UUID classId, PageRequest page) {
        return page("SELECT u.id,u.name,u.email FROM enrollment e " +
                "JOIN app_user u ON u.id=e.student_id WHERE e.class_id=? ORDER BY u.name,u.id",
                "SELECT count(*) FROM enrollment WHERE class_id=?", page, classId);
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
