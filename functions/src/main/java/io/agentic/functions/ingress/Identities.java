package io.agentic.functions.ingress;

import java.util.Locale;
import java.util.Set;

public class Identities {
    private static final Set<String> COPILOT = Set.of("copilot", "copilot-swe-agent", "copilot-swe-agent[bot]");
    private static final Set<String> COPILOT_REVIEWER = Set.of("copilot-pull-request-reviewer[bot]", "copilot-pull-request-reviewer");

    private final String serviceUser;
    private final String appBot;

    public Identities(String serviceUser, String appBot) {
        this.serviceUser = serviceUser == null ? "" : serviceUser.toLowerCase(Locale.ROOT);
        this.appBot = appBot == null ? "" : appBot.toLowerCase(Locale.ROOT);
    }

    public boolean isCopilot(String login) {
        return login != null && COPILOT.contains(login.toLowerCase(Locale.ROOT));
    }

    public boolean isCopilotReviewer(String login) {
        return login != null && COPILOT_REVIEWER.contains(login.toLowerCase(Locale.ROOT));
    }

    public boolean isServiceUser(String login) {
        return login != null && login.equalsIgnoreCase(serviceUser);
    }

    public boolean isBot(String login) {
        if (login == null) {
            return true;
        }
        String l = login.toLowerCase(Locale.ROOT);
        return l.endsWith("[bot]") || l.equals(serviceUser) || l.equals(appBot);
    }

    public boolean isHuman(String login) {
        return !isBot(login) && !isCopilot(login) && !isCopilotReviewer(login);
    }
}
