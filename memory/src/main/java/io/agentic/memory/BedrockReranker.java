package io.agentic.memory;

import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingModelConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocumentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQueryContentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSource;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSourceType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankTextDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfigurationType;

import java.util.List;
import java.util.function.Supplier;

public class BedrockReranker implements Reranker {
    private final BedrockAgentRuntimeClient client;
    private final Supplier<String> modelArn;

    public BedrockReranker(BedrockAgentRuntimeClient client, Supplier<String> modelArn) {
        this.client = client;
        this.modelArn = modelArn;
    }

    @Override
    public List<Integer> rerank(String query, List<String> documents, int topN) {
        if (documents.isEmpty()) {
            return List.of();
        }
        List<RerankSource> sources = documents.stream().map(d -> RerankSource.builder()
                .type(RerankSourceType.INLINE)
                .inlineDocumentSource(RerankDocument.builder().type(RerankDocumentType.TEXT)
                        .textDocument(RerankTextDocument.builder().text(d).build()).build())
                .build()).toList();
        RerankRequest request = RerankRequest.builder()
                .queries(RerankQuery.builder().type(RerankQueryContentType.TEXT).textQuery(RerankTextDocument.builder().text(query).build()).build())
                .sources(sources)
                .rerankingConfiguration(RerankingConfiguration.builder()
                        .type(RerankingConfigurationType.BEDROCK_RERANKING_MODEL)
                        .bedrockRerankingConfiguration(BedrockRerankingConfiguration.builder()
                                .numberOfResults(Math.min(topN, documents.size()))
                                .modelConfiguration(BedrockRerankingModelConfiguration.builder().modelArn(modelArn.get()).build())
                                .build())
                        .build())
                .build();
        return client.rerank(request).results().stream().map(RerankResult::index).toList();
    }
}
