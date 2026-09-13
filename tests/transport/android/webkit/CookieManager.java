package android.webkit;
public class CookieManager {
    private static final CookieManager INSTANCE=new CookieManager();public static int writes;
    public static CookieManager getInstance(){return INSTANCE;}
    public void setCookie(String origin,String cookie){writes++;}
}
