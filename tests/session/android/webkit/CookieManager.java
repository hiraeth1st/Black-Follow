package android.webkit;
public class CookieManager {
    private static final CookieManager INSTANCE=new CookieManager();
    public static String value="";
    public static CookieManager getInstance(){return INSTANCE;}
    public String getCookie(String url){return value;}
}
