package android.content;
public class Context {
    public static final int MODE_PRIVATE=0;
    private final SharedPreferences preferences=new SharedPreferences();
    public SharedPreferences getSharedPreferences(String name,int mode){return preferences;}
}
