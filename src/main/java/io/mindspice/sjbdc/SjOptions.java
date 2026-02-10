package io.mindspice.sjbdc;

public class SjOptions {
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        public Builder strictNamedParameters(boolean b) { return this; }
        public SjOptions build() { return new SjOptions(); }
    }
}
