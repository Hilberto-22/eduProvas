package br.edu.avaliacoes.api.domain.dto.request;

import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "Página deve ser >= 0 e tamanho entre 1 e 100");
        }
    }

    public long offset() { return (long) page * size; }
}
