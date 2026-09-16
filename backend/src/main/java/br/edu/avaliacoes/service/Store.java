package br.edu.avaliacoes.service;

import java.util.*;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Repository
public class Store {
    public final JdbcTemplate jdbc;
    public Store(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> rows(String sql,Object... args) {
        return jdbc.query(sql,(rs,n) -> {
            Map<String,Object> row=new LinkedHashMap<>();
            var meta=rs.getMetaData();
            for(int i=1;i<=meta.getColumnCount();i++) {
                Object value=rs.getObject(i);
                if(value instanceof Timestamp t) value=t.toInstant();
                row.put(meta.getColumnLabel(i),value);
            }
            return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object... args) {
        var rows=rows(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"Registro não encontrado");
        return rows.getFirst();
    }
    public int update(String sql,Object... args) { return jdbc.update(sql,args); }
    public long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
}

