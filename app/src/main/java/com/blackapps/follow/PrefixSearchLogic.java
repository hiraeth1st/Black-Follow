package com.blackapps.follow;

import java.util.*;

/** Pure rules for completing a relationship list with bounded username and display-name searches. */
public final class PrefixSearchLogic {
    public static final String USERNAME_ALPHABET="abcdefghijklmnopqrstuvwxyz0123456789._";
    public static final String TURKISH_SEARCH_ALPHABET="çğıöşü";
    public static final String SEARCH_ALPHABET=USERNAME_ALPHABET+TURKISH_SEARCH_ALPHABET;
    /** Compatibility alias: child username partitions remain limited to legal username characters. */
    public static final String ALPHABET=USERNAME_ALPHABET;
    public static final int SEARCH_COUNT=1000;
    public static final int SPLIT_AT=40;
    public static final int ROWS_AT=40;
    public static final int MAX_DEPTH=4;
    public static final int MAX_QUERIES=260;
    public static final int ROOT_ROUNDS=2;
    public static final int MAX_TARGETED=64;
    public static final int EXTRA_REST_PASSES=2;
    private static final Locale TURKISH=Locale.forLanguageTag("tr-TR");

    private PrefixSearchLogic() {}

    public static List<String> roots() {
        ArrayList<String> result=new ArrayList<>(SEARCH_ALPHABET.length());
        for(int i=0;i<SEARCH_ALPHABET.length();i++)result.add(String.valueOf(SEARCH_ALPHABET.charAt(i)));
        return result;
    }

    public static boolean isTurkishDisplayQuery(String query) {
        if(query==null||query.isEmpty())return false;
        String lower=query.toLowerCase(TURKISH);
        for(int i=0;i<lower.length();i++)if(TURKISH_SEARCH_ALPHABET.indexOf(lower.charAt(i))>=0)return true;
        return false;
    }

    public static List<String> children(String prefix) {
        // Turkish letters cannot occur in Instagram usernames. They are one-shot display-name searches.
        if(prefix==null || prefix.length()>=MAX_DEPTH || isTurkishDisplayQuery(prefix))return Collections.emptyList();
        ArrayList<String> result=new ArrayList<>(USERNAME_ALPHABET.length());
        for(int i=0;i<USERNAME_ALPHABET.length();i++)result.add(prefix+USERNAME_ALPHABET.charAt(i));
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
            if(USERNAME_ALPHABET.indexOf(next)<0)continue;
            String child=normalized+next;
            counts.put(child,counts.getOrDefault(child,0)+1);
        }
        result.sort((a,b)->{
            int byCount=Integer.compare(counts.getOrDefault(b,0),counts.getOrDefault(a,0));
            if(byCount!=0)return byCount;
            return Integer.compare(USERNAME_ALPHABET.indexOf(a.charAt(a.length()-1)),USERNAME_ALPHABET.indexOf(b.charAt(b.length()-1)));
        });
        return result;
    }

    public static int population(String prefix,Collection<String> usernames) {
        int count=0;if(prefix==null||usernames==null||isTurkishDisplayQuery(prefix))return 0;
        for(String username:usernames)if(matches(username,prefix))count++;
        return count;
    }

    public static boolean matches(String username,String prefix) {
        if(username==null || prefix==null || prefix.isEmpty())return false;
        return username.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    /** Turkish root searches recover relationship members through their display names. */
    public static boolean matchesSearchResult(String username,String fullName,String query) {
        if(isTurkishDisplayQuery(query)) {
            String name=fullName==null?"":fullName.toLowerCase(TURKISH);
            return name.contains(query.toLowerCase(TURKISH));
        }
        return matches(username,query);
    }

    public static int targetedLimit(int missing) {
        if(missing<=0)return 0;
        return Math.min(MAX_TARGETED,Math.max(12,missing*4));
    }

    /** Split username prefixes only; Turkish display-name roots intentionally remain one-shot searches. */
    public static boolean shouldSplit(String prefix,int matchingRows,int totalRows,int knownPopulation,boolean incomplete) {
        return prefix!=null && !isTurkishDisplayQuery(prefix) && prefix.length()<MAX_DEPTH &&
            (incomplete || matchingRows<knownPopulation || matchingRows>=SPLIT_AT ||
                (matchingRows>0 && totalRows>=ROWS_AT));
    }
}
