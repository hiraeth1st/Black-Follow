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
        // Only a duration supplied by Instagram creates a deadline.
        return Math.max(0,serverDelay);
    }
    public static long until(long existing,long now,long delay) {
        return Math.max(existing,delay>Long.MAX_VALUE-now?Long.MAX_VALUE:now+delay);
    }
}
