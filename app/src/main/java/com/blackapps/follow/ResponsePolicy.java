package com.blackapps.follow;

/** Never expose remote error bodies, cookies, query strings, or exception messages to diagnostics. */
public final class ResponsePolicy {
    public static String gate(int status,String message,boolean challenge,boolean checkpoint,boolean feedback) {
        if(status==429) return "BF_RATE";
        if(challenge || checkpoint || "challenge_required".equals(message) || "checkpoint_required".equals(message)) return "BF_CHALLENGE";
        String normalized=message==null?"":message.trim().toLowerCase(java.util.Locale.ROOT);
        if("please_wait_a_few_minutes".equals(normalized) || normalized.startsWith("please wait a few minutes")) return "BF_RATE";
        if(feedback || "feedback_required".equals(normalized)) return "BF_ACTION_BLOCK";
        if(status==401 || "login_required".equals(message)) return "BF_SIGN_IN";
        if(status==403) return "BF_FORBIDDEN";
        if(status>=300 && status<400) return "BF_REDIRECT";
        return "";
    }
}
