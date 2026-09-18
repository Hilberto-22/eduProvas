package br.edu.avaliacoes.api.domain.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Explicit public contracts. Repository maps never cross the HTTP boundary.
 */
public final class Responses {

    private Responses() {
    }
    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public static <T> T from(Object value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }

    public static <T> List<T> list(List<?> values, Class<T> type) {
        return values.stream().map(value -> from(value, type)).toList();
    }

    public record Id(UUID id) {

    }

    public record CreatedSession(UUID id, String code) {

    }

    public record User(UUID id, String name, String email, String role) {

    }

    public record Identity(UUID id, String name, String role) {

    }

    public record Login(String token, Identity user) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SchoolClass(UUID id, String name, UUID teacherId, long students) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Assessment(UUID id, String title, UUID teacherId, boolean locked) {

    }

    public record Student(UUID id, String name, String email) {

    }

    public record Alternative(UUID id, String label, int position,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean correct) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Question(UUID id, UUID assessmentId, String prompt, String kind, BigDecimal points,
            int position, List<Alternative> alternatives) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Session(UUID id, UUID assessmentId, UUID classId, String code, String status,
            Instant startsAt, Instant endsAt, int durationMinutes, int maxViolations,
            String violationAction, String title, String className) {

    }

    public record History(UUID id, String status, Instant deadline, String title) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Monitor(UUID studentId, String name, UUID id, String status, Instant deadline,
            Instant lastSeen, String finishReason, long violations, long answered) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Answer(UUID attemptId, UUID questionId, UUID alternativeId, String textValue,
            BigDecimal score, String feedback, Instant updatedAt, int position,
            String prompt, String kind) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Occurrence(UUID id, UUID attemptId, String kind, Instant createdAt, boolean counted) {

    }

    public record Score(BigDecimal total, long pending) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Attempt(UUID id, UUID sessionId, UUID studentId, String status, Instant startedAt,
            Instant deadline, Instant submittedAt, String finishReason, Instant lastSeen,
            Session session, Instant serverNow, List<Question> questions, List<Answer> answers,
            List<Occurrence> occurrences, long violations, Score score, BigDecimal maxScore) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Status(UUID id, String status, Instant deadline, Instant serverNow,
            String finishReason, long violations) {

    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Saved(boolean accepted, String status, Instant savedAt) {

    }

    public record OccurrenceResult(String status, long violations) {

    }
}
