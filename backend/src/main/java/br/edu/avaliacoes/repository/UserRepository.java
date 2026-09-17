package br.edu.avaliacoes.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository extends JdbcRepositorySupport {
    public UserRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> findAll() {
        return rows("SELECT id,name,email,role FROM app_user ORDER BY name");
    }

    public Optional<Map<String, Object>> findByEmail(String email) {
        return rows("SELECT * FROM app_user WHERE email=?", email).stream().findFirst();
    }

    public void create(UUID id, String name, String email, String passwordHash, String role) {
        update("INSERT INTO app_user(id,name,email,password_hash,role) VALUES(?,?,?,?,?)",
                id, name, email, passwordHash, role);
    }

    public void createAdminIfMissing(UUID id, String email, String passwordHash) {
        update("INSERT INTO app_user(id,name,email,password_hash,role) VALUES(?,?,?,?, 'ADMIN') ON CONFLICT(email) DO NOTHING",
                id, "Administrador", email, passwordHash);
    }

    public void requireStudent(UUID studentId) {
        one("SELECT id FROM app_user WHERE id=? AND role='ALUNO'", studentId);
    }

}
