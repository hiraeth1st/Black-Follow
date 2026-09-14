package com.blackapps.follow;

import java.util.*;

/** Pure rules for completing a relationship list with disjoint username-prefix searches. */
public final class PrefixSearchLogic {
    public static final String ALPHABET="abcdefghijklmnopqrstuvwxyz0123456789._";
    public static final int SPLIT_AT=40;
    public static final int MAX_DEPTH=3;
    public static final int MAX_QUERIES=160;

    private PrefixSearchLogic() {}

    public static List<String> roots() {
        ArrayList<String> result=new ArrayList<>(ALPHABET.length());
        for(int i=0;i<ALPHABET.length();i++)result.add(String.valueOf(ALPHABET.charAt(i)));
        return result;
    }

    public static List<String> children(String prefix) {
        if(prefix==null || prefix.length()>=MAX_DEPTH)return Collections.emptyList();
        ArrayList<String> result=new ArrayList<>(ALPHABET.length());
        for(int i=0;i<ALPHABET.length();i++)result.add(prefix+ALPHABET.charAt(i));
        return result;
    }

    public static boolean matches(String username,String prefix) {
        if(username==null || prefix==null || prefix.isEmpty())return false;
        return username.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    public static boolean shouldSplit(String prefix,int matchingRows,boolean incomplete) {
        return prefix!=null && prefix.length()<MAX_DEPTH && (incomplete || matchingRows>=SPLIT_AT);
    }
}
