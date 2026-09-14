package com.blackapps.follow;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Canonical request shapes for Instagram relationship lists and relationship-scoped searches. */
public final class RelationshipRequest {
    public static final int PAGE_SIZE=200;
    public static final int MAX_PAGES=1000;

    private RelationshipRequest() {}

    private static void validate(String id,String kind) {
        if(id==null || !id.matches("[0-9]+"))throw new IllegalArgumentException("Geçersiz hesap kimliği.");
        if(!"followers".equals(kind) && !"following".equals(kind))throw new IllegalArgumentException("Geçersiz liste türü.");
    }
    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8.name());
    }

    /** Cursor pagination. The maintained client implementation caps each page at 200. */
    public static String page(String id,String kind,String rankToken,String cursor,String order) throws Exception {
        validate(id,kind);
        if(rankToken==null || rankToken.isEmpty())throw new IllegalArgumentException("Liste sıralama kimliği eksik.");
        StringBuilder path=new StringBuilder("/api/v1/friendships/").append(id).append('/').append(kind)
            .append("/?count=").append(PAGE_SIZE)
            .append("&rank_token=").append(enc(rankToken))
            .append("&search_surface=follow_list_page&query=&enable_groups=true");
        if(order!=null && !order.isEmpty())path.append("&order=").append(enc(order));
        if(cursor!=null && !cursor.isEmpty())path.append("&max_id=").append(enc(cursor));
        return path.toString();
    }

    /** Relationship search is a separate, non-cursor request. Do not attach count, rank_token or max_id. */
    public static String search(String id,String kind,String query) throws Exception {
        validate(id,kind);
        if(query==null || query.isEmpty())throw new IllegalArgumentException("Arama sorgusu boş olamaz.");
        StringBuilder path=new StringBuilder("/api/v1/friendships/").append(id).append('/').append(kind).append("/?");
        if("following".equals(kind))path.append("includes_hashtags=false&");
        path.append("search_surface=follow_list_page&query=").append(enc(query)).append("&enable_groups=true");
        return path.toString();
    }

    public static boolean optionalSearchFailure(String code) {
        return "BF_HTTP_400".equals(code) || "BF_HTTP_404".equals(code) ||
            "BF_QUERY".equals(code) || "BF_REJECTED".equals(code) || "BF_SEARCH_SCHEMA".equals(code);
    }
}
