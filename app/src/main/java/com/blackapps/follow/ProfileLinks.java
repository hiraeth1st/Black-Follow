package com.blackapps.follow;

import java.net.URI;

public final class ProfileLinks {
    public static String profile(String username) {
        if(username==null || !username.matches("[A-Za-z0-9._]{1,30}"))throw new IllegalArgumentException("Geçersiz kullanıcı adı");
        return "https://www.instagram.com/"+username+"/";
    }
    public static boolean avatar(String value) {
        try {
            URI u=new URI(value);String host=u.getHost();if(host==null)return false;host=host.toLowerCase(java.util.Locale.ROOT);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getUserInfo()==null && (u.getPort()==-1||u.getPort()==443) &&
                (host.equals("cdninstagram.com")||host.endsWith(".cdninstagram.com")||host.equals("fbcdn.net")||host.endsWith(".fbcdn.net"));
        }catch(Exception e){return false;}
    }
}
