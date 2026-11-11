package com.group_nine.asl_translator.recognition;

/** Immutable, session-local recognition output. Scores describe model support. */
public final class VowelRecognitionResult {
    public enum State { IDLE, INITIALIZING, NO_HANDS, TWO_HANDS, HOLD_STEADY, RECOGNIZED, UNKNOWN, UNAVAILABLE }
    public final long timestampMs;
    public final State state;
    public final String letter;
    public final double score;

    private VowelRecognitionResult(long timestampMs, State state, String letter, double score) {
        this.timestampMs = timestampMs; this.state = state; this.letter = letter; this.score = score;
    }

    public static VowelRecognitionResult status(long timestampMs, State state) {
        if (state == State.RECOGNIZED) throw new IllegalArgumentException("A recognized result needs a letter");
        return new VowelRecognitionResult(timestampMs, state, null, 0);
    }

    static VowelRecognitionResult recognized(long timestampMs, int label, double score) {
        return new VowelRecognitionResult(timestampMs, State.RECOGNIZED, ForestModel.LABELS[label], score);
    }
}
