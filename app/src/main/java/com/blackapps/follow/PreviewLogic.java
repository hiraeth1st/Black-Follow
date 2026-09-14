package com.blackapps.follow;

import java.util.*;

/** Keeps the richest safe candidate preview without treating it as verified history. */
public final class PreviewLogic {
    private PreviewLogic() {}

    public static <T> LinkedHashMap<String,T> merge(Map<String,T> older,Map<String,T> fresh,int expected) {
        if(expected<0)throw new IllegalArgumentException("Geçersiz beklenen liste sayısı.");
        LinkedHashMap<String,T> oldCopy=new LinkedHashMap<>(),freshCopy=new LinkedHashMap<>();
        if(older!=null)oldCopy.putAll(older);if(fresh!=null)freshCopy.putAll(fresh);
        if(oldCopy.size()>expected || freshCopy.size()>expected)throw new IllegalArgumentException("Önizleme beklenen toplamı aşamaz.");
        LinkedHashMap<String,T> union=new LinkedHashMap<>(oldCopy);union.putAll(freshCopy);
        if(union.size()<=expected)return union;
        // A changed-but-equal profile count can make old and new partial sets incompatible.
        // In that case retain the larger individual observation; equal sizes prefer the fresh metadata.
        return freshCopy.size()>=oldCopy.size()?freshCopy:oldCopy;
    }
}
