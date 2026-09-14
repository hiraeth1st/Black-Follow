package android.content;
import java.util.*;
public class SharedPreferences {
    private final Map<String,Object> values=new HashMap<>();
    public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
    public long getLong(String k,long d){return (Long)values.getOrDefault(k,d);}
    public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
    public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
    public Editor edit(){return new Editor();}
    public class Editor {
        private final Map<String,Object> pending=new HashMap<>();
        public Editor putString(String k,String v){pending.put(k,v);return this;}
        public Editor putLong(String k,long v){pending.put(k,v);return this;}
        public Editor putInt(String k,int v){pending.put(k,v);return this;}
        public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
        public Editor remove(String k){pending.put(k,null);return this;}
        public void apply(){pending.forEach((k,v)->{if(v==null)values.remove(k);else values.put(k,v);});}
    }
}
