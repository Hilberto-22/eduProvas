package br.edu.avaliacoes.repository;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.edu.avaliacoes.api.domain.dto.request.PageRequest;
import br.edu.avaliacoes.api.domain.dto.response.PageResponse;

abstract class JdbcRepositorySupport {
    protected final JdbcTemplate jdbc;

    protected JdbcRepositorySupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected List<Map<String, Object>> rows(String sql, Object... arguments) {
        return jdbc.query(sql, (resultSet, rowNumber) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            var metadata = resultSet.getMetaData();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                Object value = resultSet.getObject(column);
                if (value instanceof Timestamp timestamp) {
                    value = timestamp.toInstant();
                }
                row.put(metadata.getColumnLabel(column), value);
            }
            return row;
        }, arguments);
    }

    protected Map<String, Object> one(String sql, Object... arguments) {
        var result = rows(sql, arguments);
        if (result.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Registro não encontrado");
        }
        return result.getFirst();
    }

    protected long count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    protected PageResponse<Map<String, Object>> page(String sql, String countSql, PageRequest page,
                                                     Object... arguments) {
        Object[] pagedArguments = Arrays.copyOf(arguments, arguments.length + 2);
        pagedArguments[arguments.length] = page.size();
        pagedArguments[arguments.length + 1] = page.offset();
        return new PageResponse<>(rows(sql + " LIMIT ? OFFSET ?", pagedArguments),
                page.page(), page.size(), count(countSql, arguments));
    }

    protected int update(String sql, Object... arguments) {
        return jdbc.update(sql, arguments);
    }
}
