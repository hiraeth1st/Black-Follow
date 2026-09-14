package com.blackapps.follow;

import java.util.*;

/** Pure rules for completing a relationship list with bounded username-prefix searches. */
public final class PrefixSearchLogic {
    public static final String ALPHABET="abcdefghijklmnopqrstuvwxyz0123456789._";
    public static final int SPLIT_AT=40;
    public static final int ROWS_AT=40;
    public static final int MAX_DEPTH=4;
    public static final int MAX_QUERIES=220;
    public static final int MAX_TARGETED=64;

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

    /** Dense, already observed username groups are queried first; zero-population groups remain as fallbacks. */
    public static List<String> prioritizedChildren(String prefix,Collection<String> usernames) {
        ArrayList<String> result=new ArrayList<>(children(prefix));
        HashMap<String,Integer> counts=new HashMap<>();
        String normalized=prefix==null?"":prefix.toLowerCase(Locale.ROOT);
        if(usernames!=null)for(String username:usernames) {
            if(username==null)continue;
            String lower=username.toLowerCase(Locale.ROOT);
            if(!lower.startsWith(normalized) || lower.length()<=normalized.length())continue;
            char next=lower.charAt(normalized.length());
            if(ALPHABET.indexOf(next)<0)continue;
            String child=normalized+next;
            counts.put(child,counts.getOrDefault(child,0)+1);
        }
        result.sort((a,b)->{
            int byCount=Integer.compare(counts.getOrDefault(b,0),counts.getOrDefault(a,0));
            if(byCount!=0)return byCount;
            return Integer.compare(ALPHABET.indexOf(a.charAt(a.length()-1)),ALPHABET.indexOf(b.charAt(b.length()-1)));
        });
        return result;
    }

    public static int population(String prefix,Collection<String> usernames) {
        int count=0;if(prefix==null||usernames==null)return 0;
        for(String username:usernames)if(matches(username,prefix))count++;
        return count;
    }

    public static boolean matches(String username,String prefix) {
        if(username==null || prefix==null || prefix.isEmpty())return false;
        return username.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    public static int targetedLimit(int missing) {
        if(missing<=0)return 0;
        return Math.min(MAX_TARGETED,Math.max(12,missing*4));
    }

    public static boolean shouldSplit(String prefix,int matchingRows,int totalRows,boolean incomplete) {
        return prefix!=null && prefix.length()<MAX_DEPTH &&
            (incomplete || matchingRows>=SPLIT_AT || (matchingRows>0 && totalRows>=ROWS_AT));
    }
}
