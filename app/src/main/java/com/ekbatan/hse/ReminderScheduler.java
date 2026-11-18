package com.ekbatan.hse;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.net.Uri;

/** Single source of truth for reminder alarms. */
public final class ReminderScheduler {
    private ReminderScheduler() {}
    public static int requestCode(long id){ return (int)(id ^ (id >>> 32)); }
    public static void schedule(Context c, LibraryDb.ReminderRow r){ if(r==null||r.id<=0||r.done||r.at<=0)return; schedule(c,r.id,r.title,r.body,r.at); }
    public static void schedule(Context c,long id,String title,String body,long at){
        if(id<=0||at<=0)return; AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); if(am==null)return;
        Intent i=new Intent(c,ReminderReceiver.class).setPackage(c.getPackageName()).setData(Uri.parse("hse://reminder/"+id)).putExtra("id",id).putExtra("title",title==null?"":title).putExtra("body",body==null?"":body);
        PendingIntent p=PendingIntent.getBroadcast(c,requestCode(id),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        long when=Math.max(at,System.currentTimeMillis()+1000L);
        if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p);
        else if(Build.VERSION.SDK_INT>=23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p);
        else am.set(AlarmManager.RTC_WAKEUP,when,p);
    }
    public static void cancel(Context c,long id){if(id<=0)return;AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;Intent i=new Intent(c,ReminderReceiver.class).setPackage(c.getPackageName()).setData(Uri.parse("hse://reminder/"+id));PendingIntent p=PendingIntent.getBroadcast(c,requestCode(id),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);am.cancel(p);p.cancel();}
}
