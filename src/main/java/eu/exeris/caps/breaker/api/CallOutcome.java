package eu.exeris.caps.breaker.api;

/**
 * What a call to the upstream produced, as input to a {@link FailureClassifier}.
 *
 * @param kind       how the call ended
 * @param statusCode the HTTP status code for {@link Kind#RESPONSE}, otherwise 0
 * @since 0.1.0
 */
public record CallOutcome(Kind kind, int statusCode) {

    /** How a call ended. */
    public enum Kind {
        /** The upstream answered with an HTTP status. */
        RESPONSE,
        /** The connection or the transport failed before a status was received. */
        TRANSPORT_ERROR,
        /** The call did not complete within its deadline. */
        TIMEOUT
    }

    public CallOutcome {
        java.util.Objects.requireNonNull(kind, "kind");
    }

    /** An upstream answer with the given HTTP status. */
    public static CallOutcome response(int statusCode) {
        return new CallOutcome(Kind.RESPONSE, statusCode);
    }

    /** A transport-level failure. */
    public static CallOutcome transportError() {
        return new CallOutcome(Kind.TRANSPORT_ERROR, 0);
    }

    /** A call that timed out. */
    public static CallOutcome timeout() {
        return new CallOutcome(Kind.TIMEOUT, 0);
    }
}
