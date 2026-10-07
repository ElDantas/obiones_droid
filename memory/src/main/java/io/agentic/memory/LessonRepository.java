package io.agentic.memory;

import io.agentic.core.text.Scrubber;
import io.agentic.integrations.llm.Embeddings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LessonRepository {
    public static final double DEDUP_SIMILARITY = 0.9;
    static final String SEP = "chr(31)";
    static final String COLUMNS = "CAST(id AS text) AS id, repo, array_to_string(paths, " + SEP + ") AS paths, component, language, "
            + "array_to_string(tags, " + SEP + ") AS tags, kind, trigger, lesson, array_to_string(evidence, " + SEP + ") AS evidence, "
            + "hits, helped, ignored, EXTRACT(EPOCH FROM created_at) AS created_epoch, EXTRACT(EPOCH FROM last_used_at) AS last_used_epoch, status, scope";

    public record WriteResult(String id, boolean created) {
    }

    private final SqlExecutor sql;
    private final Embeddings embeddings;

    public LessonRepository(SqlExecutor sql, Embeddings embeddings) {
        this.sql = sql;
        this.embeddings = embeddings;
    }

    public WriteResult upsert(LessonDraft draft) {
        String trigger = Scrubber.scrub(draft.trigger());
        String lesson = Scrubber.scrub(draft.lesson());
        String evidence = draft.evidence() == null ? "" : Scrubber.scrub(draft.evidence());
        String vector = vector(embeddings.embed(trigger + "\n" + lesson));
        String scope = draft.scope() == null ? "repo" : draft.scope();

        Map<String, Object> nearParams = new HashMap<>();
        nearParams.put("e", vector);
        nearParams.put("repo", draft.repo());
        nearParams.put("scope", scope);
        List<Map<String, Object>> nearest = sql.query("""
                SELECT CAST(id AS text) AS id, 1 - (embedding <=> CAST(:e AS vector)) AS sim
                FROM lessons
                WHERE status <> 'expired'
                  AND (repo IS NOT DISTINCT FROM CAST(:repo AS text) OR (CAST(:scope AS text) = 'shared' AND scope = 'shared'))
                ORDER BY embedding <=> CAST(:e AS vector)
                LIMIT 1""", nearParams);
        if (!nearest.isEmpty() && ((Number) nearest.get(0).get("sim")).doubleValue() > DEDUP_SIMILARITY) {
            String id = (String) nearest.get(0).get("id");
            sql.update("UPDATE lessons SET hits = hits + 1, evidence = array_append(evidence, CAST(:ev AS text)) WHERE id = CAST(:id AS uuid)",
                    Map.of("ev", evidence, "id", id));
            return new WriteResult(id, false);
        }
        Map<String, Object> p = new HashMap<>();
        p.put("repo", draft.repo());
        p.put("paths", pgArray(draft.paths()));
        p.put("component", draft.component());
        p.put("language", draft.language());
        p.put("tags", pgArray(draft.tags()));
        p.put("kind", draft.kind());
        p.put("trigger", trigger);
        p.put("lesson", lesson);
        p.put("evidence", pgArray(evidence.isEmpty() ? List.of() : List.of(evidence)));
        p.put("e", vector);
        p.put("scope", scope);
        List<Map<String, Object>> rows = sql.query("""
                INSERT INTO lessons (repo, paths, component, language, tags, kind, trigger, lesson, evidence, embedding, scope)
                VALUES (CAST(:repo AS text), CAST(:paths AS text[]), CAST(:component AS text), CAST(:language AS text), CAST(:tags AS text[]),
                        :kind, :trigger, :lesson, CAST(:evidence AS text[]), CAST(:e AS vector), :scope)
                RETURNING CAST(id AS text) AS id""", p);
        return new WriteResult((String) rows.get(0).get("id"), true);
    }

    public String upsertBySource(String sourceRef, LessonDraft draft) {
        String trigger = Scrubber.scrub(draft.trigger());
        String lesson = Scrubber.scrub(draft.lesson());
        Map<String, Object> p = new HashMap<>();
        p.put("ref", sourceRef);
        p.put("repo", draft.repo());
        p.put("paths", pgArray(draft.paths()));
        p.put("component", draft.component());
        p.put("language", draft.language());
        p.put("tags", pgArray(draft.tags()));
        p.put("kind", draft.kind());
        p.put("trigger", trigger);
        p.put("lesson", lesson);
        p.put("evidence", pgArray(draft.evidence() == null ? List.of() : List.of(draft.evidence())));
        p.put("e", vector(embeddings.embed(trigger + "\n" + lesson)));
        p.put("scope", draft.scope() == null ? "repo" : draft.scope());
        List<Map<String, Object>> rows = sql.query("""
                INSERT INTO lessons (source_ref, repo, paths, component, language, tags, kind, trigger, lesson, evidence, embedding, scope)
                VALUES (:ref, CAST(:repo AS text), CAST(:paths AS text[]), CAST(:component AS text), CAST(:language AS text), CAST(:tags AS text[]),
                        :kind, :trigger, :lesson, CAST(:evidence AS text[]), CAST(:e AS vector), :scope)
                ON CONFLICT (source_ref) DO UPDATE SET repo = EXCLUDED.repo, paths = EXCLUDED.paths, component = EXCLUDED.component,
                        language = EXCLUDED.language, tags = EXCLUDED.tags, kind = EXCLUDED.kind, trigger = EXCLUDED.trigger,
                        lesson = EXCLUDED.lesson, evidence = EXCLUDED.evidence, embedding = EXCLUDED.embedding, scope = EXCLUDED.scope
                RETURNING CAST(id AS text) AS id""", p);
        return (String) rows.get(0).get("id");
    }

    public Map<String, List<String>> weekActivity(Instant from, Instant to) {
        Map<String, Object> p = Map.of("f", from.getEpochSecond(), "t", to.getEpochSecond());
        Map<String, List<String>> out = new HashMap<>();
        out.put("new", texts(sql.query("SELECT trigger, lesson FROM lessons WHERE kind NOT IN ('adr','spec') AND created_at >= to_timestamp(:f) AND created_at < to_timestamp(:t) ORDER BY created_at", p)));
        out.put("promoted", texts(sql.query("SELECT trigger, lesson FROM lessons WHERE status = 'promoted' AND status_changed_at >= to_timestamp(:f) AND status_changed_at < to_timestamp(:t)", p)));
        out.put("expired", texts(sql.query("SELECT trigger, lesson FROM lessons WHERE status = 'expired' AND status_changed_at >= to_timestamp(:f) AND status_changed_at < to_timestamp(:t)", p)));
        return out;
    }

    private static List<String> texts(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> r.get("trigger") + " → " + r.get("lesson")).toList();
    }

    public Optional<LessonRecord> get(String id) {
        List<Map<String, Object>> rows = sql.query("SELECT " + COLUMNS + " FROM lessons WHERE id = CAST(:id AS uuid)", Map.of("id", id));
        return rows.isEmpty() ? Optional.empty() : Optional.of(toRecord(rows.get(0)));
    }

    public List<LessonRecord> find(String whereClause, Map<String, Object> params) {
        return sql.query("SELECT " + COLUMNS + " FROM lessons WHERE " + whereClause, params).stream().map(LessonRepository::toRecord).toList();
    }

    public void markUsed(List<String> ids, Instant now) {
        if (ids.isEmpty()) {
            return;
        }
        sql.update("UPDATE lessons SET last_used_at = to_timestamp(:t) WHERE id = ANY(CAST(:ids AS uuid[]))",
                Map.of("t", now.getEpochSecond(), "ids", pgArray(ids)));
    }

    public void scoreHelped(String id) {
        sql.update("UPDATE lessons SET helped = helped + 1 WHERE id = CAST(:id AS uuid)", Map.of("id", id));
    }

    public void scoreIgnored(String id) {
        sql.update("UPDATE lessons SET ignored = ignored + 1 WHERE id = CAST(:id AS uuid)", Map.of("id", id));
    }

    public void setStatus(String id, String status) {
        sql.update("UPDATE lessons SET status = :s, status_changed_at = now() WHERE id = CAST(:id AS uuid)", Map.of("s", status, "id", id));
    }

    public void addTag(String id, String tag) {
        sql.update("UPDATE lessons SET tags = array_append(tags, CAST(:tag AS text)) WHERE id = CAST(:id AS uuid) AND NOT (CAST(:tag AS text) = ANY(tags))",
                Map.of("tag", tag, "id", id));
    }

    public static String vector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    static String pgArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(values.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    static LessonRecord toRecord(Map<String, Object> r) {
        return new LessonRecord(
                (String) r.get("id"),
                (String) r.get("repo"),
                split(r.get("paths")),
                (String) r.get("component"),
                (String) r.get("language"),
                split(r.get("tags")),
                (String) r.get("kind"),
                (String) r.get("trigger"),
                (String) r.get("lesson"),
                split(r.get("evidence")),
                ((Number) r.get("hits")).intValue(),
                ((Number) r.get("helped")).intValue(),
                ((Number) r.get("ignored")).intValue(),
                epoch(r.get("created_epoch")),
                epoch(r.get("last_used_epoch")),
                (String) r.get("status"),
                (String) r.get("scope"));
    }

    private static List<String> split(Object v) {
        if (v == null || v.toString().isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(Arrays.asList(v.toString().split("\u001f")));
    }

    private static Instant epoch(Object v) {
        if (v == null) {
            return null;
        }
        double seconds = v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString());
        return Instant.ofEpochMilli((long) (seconds * 1000));
    }
}
