from pathlib import Path

monitor=Path('app/src/main/java/com/blackapps/follow/Monitor.java')
text=monitor.read_text(encoding='utf-8')
old='''                    if(store.commit(a,snapshot,Session.interval(c),force&&onlyId>0)) {Session.dataSucceeded(c);MonitorJob.cancelStrict(c);ok++;ChangeNotifications.post(c,store,a,before);if(onlyId>0)changes=a.lastSuccess==0?"\\nİlk tam liste kaydedildi. Sonraki yenilemelerde değişiklikler gösterilecek.":store.changes(a.id,owner,before);}
'''
new='''                    if(store.commit(a,snapshot,Session.interval(c),force&&onlyId>0)) {Session.dataSucceeded(c);ok++;ChangeNotifications.post(c,store,a,before);if(onlyId>0)changes=a.lastSuccess==0?"\\nİlk tam liste kaydedildi. Sonraki yenilemelerde değişiklikler gösterilecek.":store.changes(a.id,owner,before);}
'''
if text.count(old)!=1:raise SystemExit(f'per-account strict cancel: {text.count(old)}')
text=text.replace(old,new,1)
needle='''            if(onlyId>0 && previews>0)return lastError;
'''
replacement='''            if(previews==0)MonitorJob.cancelStrict(c);
            if(onlyId>0 && previews>0)return lastError;
'''
if text.count(needle)!=1:raise SystemExit(f'end-of-run strict cancel: {text.count(needle)}')
monitor.write_text(text.replace(needle,replacement,1),encoding='utf-8')

job=Path('app/src/main/java/com/blackapps/follow/MonitorJob.java')
job.write_text(r'''package com.blackapps.follow;

import android.app.job.*;
import android.content.*;

public class MonitorJob extends JobService {
    private static final int PERIODIC_JOB=101,STRICT_JOB=102;
    private static final String STRICT_NEEDED="strict_retry_needed";
    private Thread worker;
    private static JobScheduler scheduler(Context c){return (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);}
    public static void schedule(Context c) {
        JobScheduler scheduler=scheduler(c);
        if(!Session.prefs(c).getBoolean("automatic",true)) {
            Session.prefs(c).edit().putBoolean(STRICT_NEEDED,false).apply();
            scheduler.cancel(PERIODIC_JOB);scheduler.cancel(STRICT_JOB);return;
        }
        JobInfo existing=scheduler.getPendingJob(PERIODIC_JOB);
        if(existing!=null && existing.getIntervalMillis()==Session.interval(c)) return;
        scheduler.schedule(new JobInfo.Builder(PERIODIC_JOB,new ComponentName(c,MonitorJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(Session.interval(c)).setBackoffCriteria(3600000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public static void scheduleStrict(Context c) {
        Session.prefs(c).edit().putBoolean(STRICT_NEEDED,true).apply();
        if(!Session.prefs(c).getBoolean("automatic",true))return;
        JobScheduler scheduler=scheduler(c);if(scheduler.getPendingJob(STRICT_JOB)!=null)return;
        scheduler.schedule(new JobInfo.Builder(STRICT_JOB,new ComponentName(c,MonitorJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setMinimumLatency(30*60000L).setOverrideDeadline(2*3600000L)
            .setBackoffCriteria(30*60000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public static void cancelStrict(Context c){Session.prefs(c).edit().putBoolean(STRICT_NEEDED,false).apply();scheduler(c).cancel(STRICT_JOB);}
    public boolean onStartJob(JobParameters params) {
        final Context app=getApplicationContext();final boolean strict=params.getJobId()==STRICT_JOB;
        if(strict)Session.prefs(app).edit().putBoolean(STRICT_NEEDED,false).apply();
        worker=new Thread(()->{
            Monitor.run(app,0,false);
            jobFinished(params,false);
            // A partial result can request another strict pass while this job ID is still active.
            if(strict&&Session.prefs(app).getBoolean(STRICT_NEEDED,false)) {
                scheduler(app).cancel(STRICT_JOB);scheduleStrict(app);
            }
        },strict?"black-follow-strict-job":"black-follow-job");worker.start();return true;
    }
    public boolean onStopJob(JobParameters params) {
        if(worker!=null)worker.interrupt();
        if(params.getJobId()==STRICT_JOB)Session.prefs(this).edit().putBoolean(STRICT_NEEDED,true).apply();
        return true;
    }
}
''',encoding='utf-8')
