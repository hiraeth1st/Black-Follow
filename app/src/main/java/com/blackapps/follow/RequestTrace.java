package com.blackapps.follow;

/** Diagnostics contain only fixed categories; never a URL, username, cookie or response body. */
public final class RequestTrace {
    public static String stage(String path) {
        if(path.startsWith("/api/v1/users/web_profile_info/"))return "Profil sayıları (web)";
        if(path.startsWith("/api/v1/users/"))return "Profil sayıları (kimlik)";
        if(path.startsWith("/api/v1/friendships/")&&path.contains("/followers/"))return "Takipçi listesi";
        if(path.startsWith("/api/v1/friendships/")&&path.contains("/following/"))return "Takip edilenler listesi";
        if(path.startsWith("/web/search/topsearch/"))return "Hesap adı araması";
        if(path.startsWith("/graphql/") && path.contains("doc_id="))return "Profil sayıları (GraphQL)";
        if(path.startsWith("/graphql/"))return "Oturum doğrulama";
        return "Instagram isteği";
    }
    public static String detail(String path,int status,String gate) {
        String reason="BF_RATE".equals(gate)?"İstek sınırı":"BF_ACTION_BLOCK".equals(gate)?"İşlem kısıtlaması":"BF_CHALLENGE".equals(gate)?"Güvenlik doğrulaması":"BF_SIGN_IN".equals(gate)?"Giriş gerekiyor":"BF_FORBIDDEN".equals(gate)?"Erişim reddi":"BF_REDIRECT".equals(gate)?"Yönlendirme":"Yanıt işlenemedi";
        return "Aşama: "+stage(path)+" • "+(status>=100&&status<=599?"HTTP "+status:"HTTP yanıtı yok")+" • "+reason;
    }
}
