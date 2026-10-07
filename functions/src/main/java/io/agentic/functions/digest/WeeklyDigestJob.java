package io.agentic.functions.digest;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.functions.config.Env;
import io.agentic.functions.config.Params;
import io.agentic.functions.config.Services;
import io.agentic.integrations.confluence.ConfluenceClient;
import io.agentic.integrations.slack.SlackClient;
import io.agentic.memory.LessonRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

public class WeeklyDigestJob implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final LedgerReader ledger;
    private final LessonRepository lessons;
    private final SlackClient slack;
    private final ConfluenceClient confluence;
    private final Params params;
    private final Clock clock;
    private final DigestCalculator calculator = new DigestCalculator();
    private final DigestRenderer renderer = new DigestRenderer();

    public WeeklyDigestJob() {
        this(new LedgerReader(Services.instance().dynamo(), Env.get("LEDGER_TABLE", "agentic-ledger")), Services.instance().lessons(),
                Services.instance().slack(), Services.instance().confluence(), Services.instance().params(), Services.instance().clock());
    }

    WeeklyDigestJob(LedgerReader ledger, LessonRepository lessons, SlackClient slack, ConfluenceClient confluence, Params params, Clock clock) {
        this.ledger = ledger;
        this.lessons = lessons;
        this.slack = slack;
        this.confluence = confluence;
        this.params = params;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        LocalDate lastWeekDay = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusWeeks(1);
        LocalDate monday = lastWeekDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        String week = String.format("%d-W%02d", lastWeekDay.get(IsoFields.WEEK_BASED_YEAR), lastWeekDay.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));

        Map<String, List<String>> activity;
        try {
            activity = lessons.weekActivity(from, to);
        } catch (RuntimeException e) {
            activity = Map.of();
        }
        DigestData data = calculator.compute(week, ledger.week(week), activity);
        slack.post(params.find("/agentic/slack/channels/dev").orElse("#agentic-dev"), null, renderer.slackBlocks(data), renderer.slackText(data));
        String parent = params.find("/agentic/confluence/digestParentId").orElse("unset");
        String space = params.find("/agentic/confluence/digestSpace").orElse("unset");
        if (!"unset".equals(parent) && !"unset".equals(space)) {
            confluence.createOrUpdatePage(space, parent, "Agentic digest " + week, renderer.confluenceStorage(data));
        }
        return Map.of("week", week, "started", data.started(), "merged", data.merged());
    }
}
