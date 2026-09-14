package com.blackapps.follow;
public class InstagramClient {
    public static class AccessError extends Exception {
        public final long retryAfterMs;
        public AccessError(long delay){super("Fixture HTTP 429 [BF_RATE]");retryAfterMs=delay;}
    }
}
