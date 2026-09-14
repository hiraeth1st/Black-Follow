package com.blackapps.follow;
import android.content.Context;
public class Session {
    public static final String ORIGIN="https://www.instagram.com";
    public static String lastDetail="";
    public static boolean matches(String owner){return "123".equals(owner);}
    public static String cookies(){return "sessionid=DO_NOT_LOG";}
    public static String cookieValue(String c,String k){return "";}
    public static Prefs prefs(Context c){return new Prefs();}
    public static class Prefs {
        public String getString(String key,String fallback){return fallback;}
        public Prefs edit(){return this;}
        public Prefs putString(String k,String v){lastDetail=v;return this;}
        public Prefs remove(String key){return this;}
        public void apply(){}
    }
}
