package io.agentic.functions.metrics;

public final class Metrics {
    private Metrics() {
    }

    public static void count(String name, String dimensionValue) {
        System.out.println("{\"_aws\":{\"Timestamp\":" + System.currentTimeMillis()
                + ",\"CloudWatchMetrics\":[{\"Namespace\":\"Agentic\",\"Dimensions\":[[\"Source\"]],\"Metrics\":[{\"Name\":\"" + name
                + "\",\"Unit\":\"Count\"}]}]},\"Source\":\"" + dimensionValue + "\",\"" + name + "\":1}");
    }
}
