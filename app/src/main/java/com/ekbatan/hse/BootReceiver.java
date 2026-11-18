package com.ekbatan.hse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a=intent==null?null:intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(a)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(a))return;
        final PendingResult pending=goAsync(); final Context app=context.getApplicationContext();
        new Thread(()->{LibraryDb db=null;try{db=new LibraryDb(app);for(LibraryDb.ReminderRow r:db.pendingReminders(System.currentTimeMillis()))ReminderScheduler.schedule(app,r);}catch(Exception ignored){}finally{if(db!=null)db.close();pending.finish();}},"hse-reminder-reschedule").start();
    }
}
