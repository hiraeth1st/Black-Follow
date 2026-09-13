package com.blackapps.follow;

import android.app.job.*;
import android.content.*;

public class MonitorJob extends JobService {
    private Thread worker;
    public static void schedule(Context c) {
        JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(!Session.prefs(c).getBoolean("automatic",true)) {scheduler.cancel(101);return;}
        JobInfo existing=scheduler.getPendingJob(101);
        if(existing!=null && existing.getIntervalMillis()==Session.interval(c)) return;
        scheduler.schedule(new JobInfo.Builder(101,new ComponentName(c,MonitorJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(Session.interval(c)).setBackoffCriteria(3600000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public boolean onStartJob(JobParameters params) {
        worker=new Thread(()->{Monitor.run(getApplicationContext(),0,false);jobFinished(params,false);},"black-follow-job");worker.start();return true;
    }
    public boolean onStopJob(JobParameters params) {if(worker!=null) worker.interrupt();return true;}
}
