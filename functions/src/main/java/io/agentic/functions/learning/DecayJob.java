package io.agentic.functions.learning;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.agentic.functions.config.Services;
import io.agentic.memory.LessonRecord;
import io.agentic.memory.LessonRepository;

import java.time.Clock;
import java.util.List;
import java.util.Map;

public class DecayJob implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    private final LessonRepository repository;
    private final Clock clock;

    public DecayJob() {
        this(Services.instance().lessons(), Services.instance().clock());
    }

    DecayJob(LessonRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        List<LessonRecord> active = repository.find("status = 'active' AND kind NOT IN ('adr','spec')", Map.of());
        int expired = 0;
        for (LessonRecord l : active) {
            if (PromotionPolicy.isExpired(l, clock.instant())) {
                repository.setStatus(l.id(), "expired");
                expired++;
            }
        }
        return Map.of("checked", active.size(), "expired", expired);
    }
}
