package com.blackapps.follow;

import java.util.*;

/** Pure logic: identity is Instagram's numeric user ID, never the username. */
public final class RelationLogic {
    public static final int MAX_EXPECTED=100000;
    public static final class PageIssue extends IllegalArgumentException {
        public final boolean recoverable;
        PageIssue(String message,boolean recoverable){super(message);this.recoverable=recoverable;}
    }
    public static boolean recoverable(Throwable error){return error instanceof PageIssue && ((PageIssue)error).recoverable;}
    public static final class Change {
        public final Set<String> added, removed;
        Change(Set<String> a, Set<String> r) { added=a; removed=r; }
    }
    public static Change compare(Set<String> previous, Set<String> current) {
        Set<String> added=new LinkedHashSet<>(), removed=new LinkedHashSet<>();
        if (previous != null) {
            added.addAll(current); added.removeAll(previous);
            removed.addAll(previous); removed.removeAll(current);
        }
        return new Change(added, removed);
    }
    public static final class Pages {
        private final int expected;
        private final Set<String> ids=new HashSet<>(), cursors=new HashSet<>();
        private boolean finished;private int stagnantPages;
        public int count(){return ids.size();}
        public Pages(int expected) {
            if(expected<0 || expected>MAX_EXPECTED) throw new IllegalArgumentException("Bu sürüm liste başına en fazla "+MAX_EXPECTED+" kişiyi doğrular.");
            this.expected=expected;
        }
        public void add(Collection<String> page, String next, boolean more) {
            if(finished) throw new PageIssue("Biten listeye yeni sayfa geldi.",false);
            int before=ids.size();
            for(String id:page) {
                if(id==null || !id.matches("[0-9]+"))throw new PageIssue("Liste geçersiz kişi kimliği içeriyor; geçmiş korunuyor. [BF_LIST_ID]",false);
                ids.add(id);
            }
            stagnantPages=ids.size()==before?stagnantPages+1:0;
            if(ids.size()>expected) throw new PageIssue("Liste kontrol sırasında değişti; geçmiş korunuyor.",false);
            if(more && (page.isEmpty() || next.isEmpty() || !cursors.add(next)))
                throw new PageIssue("Liste imleci tamamlanamadı; bağımsız kurtarma geçişi denenecek. [BF_LIST_CURSOR]",true);
            if(more && stagnantPages>=3)
                throw new PageIssue("Liste üç sayfadır ilerlemiyor ("+ids.size()+"/"+expected+"); bağımsız kurtarma geçişi denenecek. [BF_LIST_STALLED]",true);
            finished=!more;
        }
        public void finish() {
            if(!finished || ids.size()!=expected) throw new PageIssue("Listenin tamamı alınamadı ("+ids.size()+"/"+expected+"); geçmiş korunuyor.",true);
        }
    }
}