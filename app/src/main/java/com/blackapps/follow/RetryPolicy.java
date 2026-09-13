package com.blackapps.follow;

import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.*;

/** Retry-After is a minimum wait, never a promise that access will work at that time. */
public final class RetryPolicy {
    public static long serverDelay(String value,long now) {
        if(value==null || value.trim().isEmpty()) return -1;
        value=value.trim();
        if(value.matches("[0-9]+")) {
            try {return Math.multiplyExact(Long.parseLong(value),1000L);}catch(ArithmeticException|NumberFormatException e){return Long.MAX_VALUE-now;}
        }
        String[] formats={"EEE, dd MMM yyyy HH:mm:ss zzz","EEEE, dd-MMM-yy HH:mm:ss zzz","EEE MMM d HH:mm:ss yyyy"};
        for(String format:formats) {
            SimpleDateFormat parser=new SimpleDateFormat(format,Locale.US);parser.setTimeZone(TimeZone.getTimeZone("GMT"));parser.setLenient(false);
            ParsePosition pos=new ParsePosition(0);Date parsed=parser.parse(value,pos);
            if(parsed!=null && pos.getIndex()==value.length()) return Math.max(0,parsed.getTime()-now);
        }
        return -1;
    }
    public static long delay(long serverDelay,int failures) {
        // A conservative local policy when the server did not provide a duration.
        long local=Math.min(86400000L,15*60000L*(1L<<Math.min(Math.max(0,failures),7)));
        return serverDelay>=0?Math.max(60000L,serverDelay):local;
    }
    public static long until(long existing,long now,long delay) {
        return Math.max(existing,delay>Long.MAX_VALUE-now?Long.MAX_VALUE:now+delay);
    }
}
