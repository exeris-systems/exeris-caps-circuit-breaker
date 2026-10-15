package eu.exeris.caps.breaker.api;

/** The classification returned by {@link FailureClassifier#defaults()}. */
enum DefaultFailureClassifier implements FailureClassifier {
    INSTANCE;

    @Override
    public boolean isFailure(CallOutcome outcome) {
        return switch (outcome.kind()) {
            case TRANSPORT_ERROR, TIMEOUT -> true;
            case RESPONSE -> switch (outcome.statusCode()) {
                case 500, 502, 503, 504 -> true;
                default -> false;
            };
        };
    }
}
