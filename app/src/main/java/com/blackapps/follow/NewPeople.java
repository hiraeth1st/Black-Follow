package com.blackapps.follow;

/** Builds one bounded notification per completed account scan, never for a baseline. */
public final class NewPeople {
    public int followers,following,leftFollowers;private final boolean includeFollowerRemovals;
    public NewPeople(){this(false);}
    public NewPeople(boolean includeFollowerRemovals){this.includeFollowerRemovals=includeFollowerRemovals;}
    private int shown;private final StringBuilder lines=new StringBuilder();
    public void add(boolean baseline,String kind,String action,String username) {
        if(baseline)return;
        if(includeFollowerRemovals && "followers".equals(kind) && "removed".equals(action)) {
            leftFollowers++;if(shown++<15){if(lines.length()>0)lines.append('\n');lines.append('@').append(username).append(" takipçi listenden çıktı");}return;
        }
        if(!"added".equals(action))return;
        if("followers".equals(kind))followers++;else if("following".equals(kind))following++;else return;
        if(shown++<15) {if(lines.length()>0)lines.append('\n');lines.append('@').append(username).append("followers".equals(kind)?" takipçi olarak eklendi":" takip edildi");}
    }
    public int total(){return followers+following+leftFollowers;}
    public String title(){return followers+" yeni takipçi • "+following+" yeni takip"+(leftFollowers>0?" • "+leftFollowers+" takipçi çıktı":"");}
    public String body(){return lines+(total()>15?"\nDiğer "+(total()-15)+" kişi Hareketler sekmesinde.":"");}
}
