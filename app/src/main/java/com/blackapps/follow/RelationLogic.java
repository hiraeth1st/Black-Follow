package com.blackapps.follow;

import java.util.*;

/** Pure logic: identity is Instagram's numeric user ID, never the username. */
public final class RelationLogic {
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
        private boolean finished;
        public Pages(int expected) {
            if(expected<0 || expected>10000) throw new IllegalArgumentException("Bu test sürümü liste başına en fazla 10.000 kişiyi destekliyor.");
            this.expected=expected;
        }
        public void add(Collection<String> page, String next, boolean more) {
            if(finished) throw new IllegalArgumentException("Biten listeye yeni sayfa geldi.");
            for(String id:page) if(!id.matches("[0-9]+") || !ids.add(id))
                throw new IllegalArgumentException("Liste tekrar eden veya geçersiz kayıt içeriyor; geçmiş korunuyor.");
            if(ids.size()>expected) throw new IllegalArgumentException("Liste kontrol sırasında değişti; geçmiş korunuyor.");
            if(more && (page.isEmpty() || next.isEmpty() || !cursors.add(next)))
                throw new IllegalArgumentException("Liste tamamlanamadı; geçmiş korunuyor.");
            finished=!more;
        }
        public void finish() {
            if(!finished || ids.size()!=expected) throw new IllegalArgumentException("Listenin tamamı alınamadı ("+ids.size()+"/"+expected+"); geçmiş korunuyor.");
        }
    }
}
