package com.blackapps.follow;

import java.net.URI;
import org.json.JSONTokener;

/** Validates a DOM result from the trusted WebView against its navigation and cookie identity. */
public final class WebSession {
    public static boolean trusted(String url) {
        try {
            URI u=new URI(url);String host=u.getHost(),path=u.getPath();
            return "https".equals(u.getScheme()) && ("www.instagram.com".equals(host)||"instagram.com".equals(host))
                && u.getUserInfo()==null && (u.getPort()==-1 || u.getPort()==443) && path!=null
                && !path.matches("^/(accounts|challenge|checkpoint|oauth)(/.*)?$");
        } catch(Exception e) {return false;}
    }
    public static String verifiedName(String beforeUrl,String afterUrl,String beforeOwner,String afterOwner,boolean hasSession,String result) {
        if(!trusted(beforeUrl) || !beforeUrl.equals(afterUrl) || !beforeOwner.matches("[0-9]+") || !beforeOwner.equals(afterOwner) || !hasSession) return "";
        try {
            Object value=new JSONTokener(result).nextValue();
            return value instanceof String && ((String)value).matches("[A-Za-z0-9._]{1,30}")?(String)value:"";
        } catch(Exception e) {return "";}
    }
}
