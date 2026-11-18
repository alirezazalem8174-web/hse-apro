package com.ekbatan.hse;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL="hse_reminders";
    @Override public void onReceive(Context c,Intent i){
        long id=i.getLongExtra("id",-1L); if(id<=0)return;
        LibraryDb db=null;
        try{
            db=new LibraryDb(c); LibraryDb.ReminderRow r=db.getReminder(id); if(r==null||r.done)return;
            if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
            NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE); if(nm==null)return;
            if(Build.VERSION.SDK_INT>=24 && !nm.areNotificationsEnabled())return;
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel ch=nm.getNotificationChannel(CHANNEL);
                if(ch==null){ch=new NotificationChannel(CHANNEL,"یادآوری‌های HSE",NotificationManager.IMPORTANCE_HIGH);nm.createNotificationChannel(ch);}
                if(ch.getImportance()==NotificationManager.IMPORTANCE_NONE)return;
            }
            Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
            Notification n=b.setContentTitle(r.title.isEmpty()?"یادآوری HSE":r.title).setContentText(r.body).setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).build();
            nm.notify(ReminderScheduler.requestCode(id),n); db.setReminderDone(id,true);
        }catch(SecurityException ignored){}catch(Exception ignored){}finally{if(db!=null)db.close();}
    }
}
