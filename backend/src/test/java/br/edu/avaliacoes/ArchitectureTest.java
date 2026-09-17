package br.edu.avaliacoes;

import br.edu.avaliacoes.controller.*;
import br.edu.avaliacoes.service.*;
import br.edu.avaliacoes.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {
    private static final List<Class<?>> SERVICE_CONTRACTS = List.of(
            AuthService.class,
            UserService.class,
            SchoolClassService.class,
            AssessmentService.class,
            ExamSessionService.class,
            AttemptService.class);

    private static final List<Class<?>> SERVICE_IMPLEMENTATIONS = List.of(
            AuthServiceImpl.class,
            UserServiceImpl.class,
            SchoolClassServiceImpl.class,
            AssessmentServiceImpl.class,
            ExamSessionServiceImpl.class,
            AttemptServiceImpl.class);

    private static final List<Class<?>> CONTROLLERS = List.of(
            AuthController.class,
            AdminController.class,
            TeacherController.class,
            StudentController.class);

    @Test
    void servicesExposeInterfacesAndImplementationsDoNotAccessJdbcDirectly() {
        assertThat(SERVICE_CONTRACTS).allMatch(Class::isInterface);
        assertThat(SERVICE_IMPLEMENTATIONS)
                .allSatisfy(type -> assertThat(type.getInterfaces()).isNotEmpty())
                .flatExtracting(type -> List.of(type.getDeclaredFields()))
                .noneMatch(field -> field.getType().equals(JdbcTemplate.class));
    }

    @Test
    void controllersDependOnServicesInsteadOfRepositories() {
        assertThat(CONTROLLERS)
                .flatExtracting(type -> List.of(type.getDeclaredFields()))
                .noneMatch(field -> field.getType().getPackageName().contains("repository"));
    }
}
