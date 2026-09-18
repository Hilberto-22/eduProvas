package br.edu.avaliacoes.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssessmentRepositoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void loadsManyQuestionsInTwoQueriesWithoutSelectingTheStudentAnswerKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID assessment = UUID.randomUUID();
        List<Map<String, Object>> questions = new ArrayList<>();
        List<Map<String, Object>> alternatives = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            UUID id = UUID.randomUUID();
            questions.add(new LinkedHashMap<>(Map.of("id", id, "position", i)));
            if (i == 20) continue; // Essay: no alternatives.
            for (int position = 1; position <= 2; position++) {
                alternatives.add(new LinkedHashMap<>(Map.of("id", UUID.randomUUID(),
                        "question_id", id, "position", position, "label", "Option " + position)));
            }
        }
        when(jdbc.query(anyString(), any(RowMapper.class), eq(assessment)))
                .thenReturn(questions, alternatives);
        var result = new AssessmentRepository(jdbc).findQuestions(assessment, false);
        assertThat(result).hasSize(20);
        assertThat((List<?>) result.get(0).get("alternatives")).hasSize(2);
        assertThat((List<?>) result.get(19).get("alternatives")).isEmpty();
        verify(jdbc, times(2)).query(argThat(sql -> !sql.contains("correct") && !sql.contains("SELECT *")),
                any(RowMapper.class), eq(assessment));
        verifyNoMoreInteractions(jdbc);
    }
}
