package com.blackapps.follow;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

public final class ChangeNotifications {
    private static final String CHANNEL="new_people";
    public static final int PERMISSION_REQUEST=771;
    private static NotificationManager manager(Context c){return (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);}
    public static void initialize(Context c){
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Takipçi ve takip değişiklikleri",NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Yeni kişiler ve kendi takipçi listenden çıkanlar tespit edildiğinde bildirir.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);manager(c).createNotificationChannel(channel);
        String owner=Session.owner(c);
        if(!owner.equals(Session.prefs(c).getString("notification_owner",""))){manager(c).cancelAll();Session.prefs(c).edit().putString("notification_owner",owner).apply();}
    }
    public static boolean enabled(Context c){
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationChannel channel=manager(c).getNotificationChannel(CHANNEL);
        return manager(c).areNotificationsEnabled() && channel!=null && channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    public static void request(Activity a){
        initialize(a);
        if(Build.VERSION.SDK_INT>=33 && a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && (!Session.prefs(a).getBoolean("notification_permission_asked",false)||a.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))){
            Session.prefs(a).edit().putBoolean("notification_permission_asked",true).apply();
            a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},PERMISSION_REQUEST);return;
        }
        Intent settings=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,CHANNEL);
        try {a.startActivity(settings);}catch(ActivityNotFoundException e){android.widget.Toast.makeText(a,"Telefon ayarlarından Black Follow bildirimlerini açabilirsin.",android.widget.Toast.LENGTH_LONG).show();}
    }
    public static void cancel(Context c,long id){manager(c).cancel("account-"+id,1);}
    public static void clear(Context c){manager(c).cancelAll();}
    public static void post(Context c,Store store,Store.Account a,long after){
        // Delivery failure must never turn a committed snapshot into a failed scan.
        try {
            initialize(c);
            if(a.lastSuccess==0 || !a.owner.equals(Session.owner(c)) || !Session.matches(a.owner) || !enabled(c))return;
            NewPeople people=store.newPeople(a.id,a.owner,after);
            if(people.total()==0)return;
            Intent open=new Intent(c,MainActivity.class).setData(android.net.Uri.parse("blackfollow://account/"+a.id)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("notification_account",a.id).putExtra("notification_owner",a.owner).putExtra("notification_departures",people.leftFollowers>0);
            PendingIntent tap=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification publicVersion=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Black Follow").setContentText("Takip listelerinde değişiklik tespit edildi.").build();
            Notification alert=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("@"+a.username+" • "+people.title())
                .setContentText(people.body()).setStyle(new Notification.BigTextStyle().bigText(people.body()))
                .setContentIntent(tap).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setCategory(Notification.CATEGORY_SOCIAL).build();
            if(a.owner.equals(Session.owner(c)))manager(c).notify("account-"+a.id,1,alert);
        }catch(RuntimeException ignored){ /* OS notification settings do not affect stored data. */ }
    }
}
