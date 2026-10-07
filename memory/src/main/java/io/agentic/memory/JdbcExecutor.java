package io.agentic.memory;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdbcExecutor implements SqlExecutor {
    private static final Pattern NAMED = Pattern.compile("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)");

    private final String url;

    public JdbcExecutor(String url) {
        this.url = url;
    }

    @Override
    public List<Map<String, Object>> query(String sql, Map<String, Object> params) {
        try (Connection c = DriverManager.getConnection(url); PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    if (v instanceof Array a) {
                        v = Arrays.asList((Object[]) a.getArray());
                    }
                    row.put(md.getColumnLabel(i), v);
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int update(String sql, Map<String, Object> params) {
        try (Connection c = DriverManager.getConnection(url); PreparedStatement ps = prepare(c, sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void execute(String sql) {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Map<String, Object> params) throws SQLException {
        List<String> order = new ArrayList<>();
        Matcher m = NAMED.matcher(sql);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            order.add(m.group(1));
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        PreparedStatement ps = c.prepareStatement(sb.toString());
        for (int i = 0; i < order.size(); i++) {
            if (!params.containsKey(order.get(i))) {
                throw new IllegalArgumentException("Missing SQL parameter " + order.get(i));
            }
            ps.setObject(i + 1, params.get(order.get(i)));
        }
        return ps;
    }
}
