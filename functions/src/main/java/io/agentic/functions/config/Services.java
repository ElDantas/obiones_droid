package io.agentic.functions.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentic.functions.store.RunStore;
import io.agentic.integrations.bedrock.BedrockText;
import io.agentic.integrations.config.Secrets;
import io.agentic.integrations.confluence.ConfluenceClient;
import io.agentic.integrations.github.CopilotClient;
import io.agentic.integrations.github.GitHubAppAuth;
import io.agentic.integrations.github.GitHubClient;
import io.agentic.integrations.github.Pem;
import io.agentic.integrations.http.JsonHttp;
import io.agentic.integrations.jira.JiraClient;
import io.agentic.integrations.jira.JiraFields;
import io.agentic.integrations.llm.FakeLlm;
import io.agentic.integrations.llm.TextModel;
import io.agentic.integrations.slack.SlackClient;
import io.agentic.integrations.llm.Embeddings;
import io.agentic.memory.BedrockEmbedder;
import io.agentic.memory.BedrockReranker;
import io.agentic.memory.HybridSearch;
import io.agentic.memory.Reranker;
import io.agentic.memory.LessonRepository;
import io.agentic.memory.MemoryExecutors;
import io.agentic.memory.SqlExecutor;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.time.Clock;
import java.util.function.Supplier;

public class Services {
    private static Services instance;

    private final Clock clock = Clock.systemUTC();
    private final JsonHttp http = new JsonHttp();
    private final Supplier<DynamoDbClient> dynamo = memo(DynamoDbClient::create);
    private final Supplier<SfnClient> sfn = memo(SfnClient::create);
    private final Supplier<SsmClient> ssm = memo(SsmClient::create);
    private final Supplier<Secrets> secrets = memo(() -> new Secrets(SecretsManagerClient.create(), clock));
    private final Supplier<Params> params = memo(() -> new Params(ssm.get(), clock));
    private final Supplier<RunStore> runStore = memo(() -> new RunStore(dynamo.get(), clock,
            Env.get("RUNS_TABLE", "agentic-runs"), Env.get("LEDGER_TABLE", "agentic-ledger"), Env.get("DELIVERIES_TABLE", "agentic-deliveries")));
    private final Supplier<GitHubClient> github = memo(this::buildGitHub);
    private final Supplier<CopilotClient> copilot = memo(this::buildCopilot);
    private final Supplier<JiraClient> jira = memo(this::buildJira);
    private final Supplier<SlackClient> slack = memo(this::buildSlack);
    private final Supplier<TextModel> textModel = memo(this::buildTextModel);
    private final Supplier<SqlExecutor> memorySql = memo(this::buildMemorySql);
    private final Supplier<Embeddings> embeddings = memo(this::buildEmbeddings);
    private final Supplier<LessonRepository> lessons = memo(() -> new LessonRepository(memorySql.get(), embeddings.get()));
    private final Supplier<HybridSearch> hybridSearch = memo(() -> new HybridSearch(lessons.get(), embeddings.get(), buildReranker()));

    public static synchronized Services instance() {
        if (instance == null) {
            instance = new Services();
        }
        return instance;
    }

    public Clock clock() {
        return clock;
    }

    public JsonHttp http() {
        return http;
    }

    public SfnClient sfn() {
        return sfn.get();
    }

    public Secrets secrets() {
        return secrets.get();
    }

    public Params params() {
        return params.get();
    }

    public RunStore runStore() {
        return runStore.get();
    }

    public GitHubClient github() {
        return github.get();
    }

    public CopilotClient copilot() {
        return copilot.get();
    }

    public JiraClient jira() {
        return jira.get();
    }

    public SlackClient slack() {
        return slack.get();
    }

    public TextModel textModel() {
        return textModel.get();
    }

    public SqlExecutor memorySql() {
        return memorySql.get();
    }

    public Embeddings embeddings() {
        return embeddings.get();
    }

    public LessonRepository lessons() {
        return lessons.get();
    }

    public ConfluenceClient confluence() {
        JsonNode j = secrets().getJson("agentic/jira");
        String base = Env.find("CONFLUENCE_BASE_URL").orElse(j.path("baseUrl").asText());
        return new ConfluenceClient(http, base, j.path("email").asText(), j.path("apiToken").asText());
    }

    public void putParam(String name, String value) {
        ssm.get().putParameter(b -> b.name(name).value(value).type(software.amazon.awssdk.services.ssm.model.ParameterType.STRING).overwrite(true));
    }

    public software.amazon.awssdk.services.dynamodb.DynamoDbClient dynamo() {
        return dynamo.get();
    }

    public HybridSearch hybridSearch() {
        return hybridSearch.get();
    }

    public String githubApiUrl() {
        return Env.get("GITHUB_API_URL", "https://api.github.com");
    }

    public String serviceUserLogin() {
        return params().find("/agentic/github/serviceUser").orElse(secrets().getJson("agentic/github-service-user").path("login").asText());
    }

    public String stateMachineArn() {
        return Env.find("STATE_MACHINE_ARN").orElseGet(() -> params().get("/agentic/stateMachineArn"));
    }

    private GitHubClient buildGitHub() {
        JsonNode app = secrets().getJson("agentic/github-app");
        GitHubAppAuth auth = new GitHubAppAuth(http, githubApiUrl(), app.path("appId").asText(), app.path("installationId").asText(),
                Pem.readPrivateKey(app.path("privateKeyPem").asText()), clock);
        return new GitHubClient(http, githubApiUrl(), auth);
    }

    private CopilotClient buildCopilot() {
        return new CopilotClient(http, githubApiUrl(), () -> secrets().getJson("agentic/github-service-user").path("token").asText());
    }

    private JiraClient buildJira() {
        JsonNode j = secrets().getJson("agentic/jira");
        JiraFields fields = new JiraFields(
                params().find("/agentic/jira/fields/targetRepo").orElse("customfield_10123"),
                params().find("/agentic/jira/fields/acceptanceCriteria").orElse("none"),
                params().find("/agentic/jira/fields/storyPoints").orElse("customfield_10016"));
        String base = Env.find("JIRA_BASE_URL").orElse(j.path("baseUrl").asText());
        return new JiraClient(http, base, j.path("email").asText(), j.path("apiToken").asText(), fields);
    }

    private SlackClient buildSlack() {
        return new SlackClient(http, Env.get("SLACK_API_URL", "https://slack.com/api/"), secrets().getJson("agentic/slack").path("botToken").asText());
    }

    private TextModel buildTextModel() {
        return Env.fakeLlm() ? new FakeLlm() : new BedrockText(BedrockRuntimeClient.create());
    }

    private SqlExecutor buildMemorySql() {
        return MemoryExecutors.fromEnv(name -> Env.find(name).orElseGet(() -> switch (name) {
            case "MEMORY_CLUSTER_ARN" -> params().find("/agentic/memory/clusterArn").orElse(null);
            case "MEMORY_SECRET_ARN" -> params().find("/agentic/memory/secretArn").orElse(null);
            default -> null;
        }));
    }

    private Reranker buildReranker() {
        if (Env.fakeLlm()) {
            return Reranker.identity();
        }
        return new BedrockReranker(BedrockAgentRuntimeClient.create(), () -> {
            String model = params().find("/agentic/bedrock/rerankModelId").orElse("amazon.rerank-v1:0");
            String region = Env.get("AWS_REGION", "eu-west-2");
            return model.startsWith("arn:") ? model : "arn:aws:bedrock:" + region + "::foundation-model/" + model;
        });
    }

    private Embeddings buildEmbeddings() {
        return Env.fakeLlm() ? new FakeLlm()
                : new BedrockEmbedder(BedrockRuntimeClient.create(), () -> params().find("/agentic/bedrock/embedModelId").orElse("amazon.titan-embed-text-v2:0"));
    }

    private static <T> Supplier<T> memo(Supplier<T> delegate) {
        return new Supplier<>() {
            private T value;

            @Override
            public synchronized T get() {
                if (value == null) {
                    value = delegate.get();
                }
                return value;
            }
        };
    }
}
