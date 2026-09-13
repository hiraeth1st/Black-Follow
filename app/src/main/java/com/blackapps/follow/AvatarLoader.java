package com.blackapps.follow;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;

/** CDN thumbnails only. Instagram session cookies are never sent to image hosts. */
public final class AvatarLoader {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(120),new ThreadPoolExecutor.DiscardPolicy());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private volatile int generation;
    public AvatarLoader(Context context){this.context=context.getApplicationContext();}
    public void clearPage(){generation++;pool.getQueue().clear();}
    public void close(){clearPage();pool.shutdownNow();cache.evictAll();}
    public void load(ImageView view,String url,String owner) {
        if(!ProfileLinks.avatar(url))return;
        String key=owner+"\n"+url;view.setTag(key);Bitmap hit=cache.get(key);if(hit!=null){view.setImageBitmap(hit);return;}
        if(Session.blocked(context))return;
        int page=generation;WeakReference<ImageView> target=new WeakReference<>(view);
        pool.execute(()->{
            try {
                if(page!=generation || !owner.equals(Session.owner(context)))return;
                Bitmap b=download(url,page);if(b==null || page!=generation)return;
                cache.put(key,b);main.post(()->{ImageView v=target.get();if(v!=null&&page==generation&&key.equals(v.getTag())&&owner.equals(Session.owner(context)))v.setImageBitmap(b);});
            }catch(Exception ignored){/* Keep the explicit placeholder; no account request/retry. */}
        });
    }
    private Bitmap download(String value,int page)throws Exception {
        for(int redirect=0;redirect<3;redirect++) {
            if(!ProfileLinks.avatar(value)||page!=generation||Thread.currentThread().isInterrupted())return null;
            HttpURLConnection c=(HttpURLConnection)new URL(value).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setRequestProperty("Accept","image/*");
            try {
                int status=c.getResponseCode();
                if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)return null;value=new URL(new URL(value),next).toString();continue;}
                if(status!=200 || c.getContentLengthLong()>2*1024*1024)return null;
                String type=c.getContentType();if(type==null||!type.startsWith("image/"))return null;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>2*1024*1024||page!=generation||Thread.currentThread().isInterrupted())return null;bytes.write(buffer,0,n);}}
                byte[] data=bytes.toByteArray();BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
                if(o.outWidth<=0||o.outHeight<=0||o.outWidth>8192||o.outHeight>8192)return null;
                o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>192)o.inSampleSize*=2;
                o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(data,0,data.length,o);
            }finally{c.disconnect();}
        }
        return null;
    }
}
