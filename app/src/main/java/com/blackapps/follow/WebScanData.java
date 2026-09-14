package com.blackapps.follow;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** One unfiltered, ordered WebView traversal. No username-based identities. */
public final class WebScanData {
    public final int expected;
    public final LinkedHashMap<String,Store.Edge> people=new LinkedHashMap<>();
    private final RelationLogic.Pages validation;
    private String next="";
    private int pages;
    private boolean terminal;
    public WebScanData(int expected){this.expected=expected;validation=new RelationLogic.Pages(expected);}
    public int pages(){return pages;}
    public boolean complete(){return terminal && people.size()==expected;}
    public boolean terminal(){return terminal;}
    public void accept(JSONObject packet) throws Exception {
        String cursor=packet.getString("cursor");
        if(terminal || !next.equals(cursor))throw new IOException("Web sayfa sırası değişti veya ilk sayfa yakalanamadı. [BF_WEB_ORDER]");
        if(++pages>1000)throw new IOException("Web sayfa sınırına ulaşıldı.");
        JSONArray users=packet.getJSONArray("users");
        LinkedHashMap<String,Store.Edge> incoming=new LinkedHashMap<>();ArrayList<String> ids=new ArrayList<>();
        for(int i=0;i<users.length();i++) {
            JSONObject u=users.getJSONObject(i);String id=u.getString("id"),name=u.getString("username");
            if(!id.matches("[0-9]+") || !name.matches("[A-Za-z0-9._]{1,30}"))throw new IOException("Web kişi kimliği okunamadı. [BF_WEB_ID]");
            Store.Edge e=new Store.Edge(id,name,u.optString("name",""));String photo=u.optString("avatar","");if(ProfileLinks.avatar(photo))e.avatar=photo;
            incoming.put(id,e);ids.add(id);
        }
        String following=packet.getString("next");boolean more=packet.getBoolean("more");
        validation.add(ids,following,more);people.putAll(incoming);next=following;terminal=!more;
    }
}
