package com.ekbatan.hse;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.*;

/** Defensive, local-only backup/restore implementation. */
public final class BackupManager {
    private static final long MAX_BACKUP_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 20000;
    private static final int MAX_NAME = 1024;
    private static final int DB_VERSION = 10;
    private BackupManager() {}

    public static File create(Context c, LibraryDb db) throws Exception {
        File cache=c.getCacheDir(); File tmp=new File(cache,"backup_"+System.nanoTime()+".part"); File zip=new File(cache,"HSE_AI_PRO_Backup_"+System.currentTimeMillis()+"_"+System.nanoTime()+".zip");
        try {
            SQLiteDatabase w=db.getWritableDatabase();
            Cursor ck=w.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null); try { if(ck.moveToFirst() && ck.getInt(0)!=0) throw new IOException("checkpoint دیتابیس کامل نشد"); } finally { ck.close(); }
            if(!db.integrityOk()) throw new IOException("سلامت دیتابیس قبل از پشتیبان‌گیری تأیید نشد");
            File dbFile=c.getDatabasePath("hse.db"); if(!dbFile.isFile()) throw new IOException("فایل دیتابیس پیدا نشد");
            long total=0; if(dbFile.length()>MAX_ENTRY_BYTES) throw new IOException("حجم دیتابیس بیش از حد مجاز است");
            try(ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
                total=add(z,dbFile,"hse.db",total);
                File dir=new File(c.getFilesDir(),"library");
                if(dir.exists()) { if(!dir.isDirectory() || Files.isSymbolicLink(dir.toPath())) throw new IOException("کتابخانه PDF نامعتبر است"); total=addDir(z,dir,"library/",total,new HashSet<String>()); }
            }
            if(!tmp.isFile() || tmp.length()<=0 || tmp.length()>MAX_BACKUP_BYTES) throw new IOException("حجم Backup نامعتبر است");
            if(!tmp.renameTo(zip)) { copy(tmp,zip); if(!zip.isFile()) throw new IOException("ثبت Backup ناموفق بود"); }
            return zip;
        } catch(Exception e) { if(zip.exists()) zip.delete(); throw e; } finally { if(tmp.exists()) tmp.delete(); }
    }
    private static long addDir(ZipOutputStream z,File d,String base,long total,Set<String> names)throws Exception {
        File[] fs=d.listFiles(); if(fs==null) throw new IOException("خواندن کتابخانه PDF ناموفق بود");
        for(File f:fs){ if(Files.isSymbolicLink(f.toPath())) throw new IOException("Symlink در کتابخانه مجاز نیست"); String n=base+f.getName(); if(n.length()>MAX_NAME || n.contains("..") || n.contains("\\")) throw new IOException("نام فایل ناامن"); if(names.size()>=MAX_ENTRIES || !names.add(n)) throw new IOException("تعداد/نام فایل‌های Backup نامعتبر است"); if(f.isDirectory()) total=addDir(z,f,n+"/",total,names); else total=add(z,f,n,total); }
        return total;
    }
    private static long add(ZipOutputStream z,File f,String name,long total)throws Exception {
        if(!f.isFile() || Files.isSymbolicLink(f.toPath())) throw new IOException("فایل غیرمجاز در Backup");
        if(name.length()>MAX_NAME || name.startsWith("/") || name.contains("..") || name.contains("\\")) throw new IOException("نام فایل ناامن");
        long len=f.length(); if(len>MAX_ENTRY_BYTES || total+len>MAX_BACKUP_BYTES) throw new IOException("حجم Backup بیش از حد مجاز است");
        z.putNextEntry(new ZipEntry(name)); try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)z.write(b,0,n);} finally {z.closeEntry();} return total+len;
    }

    public static void restore(Context c,File zip) throws Exception {
        if(zip==null||!zip.isFile()) throw new IOException("فایل Backup پیدا نشد");
        if(zip.length()>MAX_BACKUP_BYTES) throw new IOException("حجم فایل Backup بیش از حد مجاز است");
        File tmp=new File(c.getCacheDir(),"restore_"+System.nanoTime()); if(!tmp.mkdirs()) throw new IOException("ساخت فضای موقت ناموفق بود");
        File staged=new File(c.getCacheDir(),"hse_restore_staged_"+System.nanoTime()+".db");
        File oldDb=c.getDatabasePath("hse.db"), oldDbBackup=new File(c.getCacheDir(),"hse_old_"+System.nanoTime()+".db");
        File oldLib=new File(c.getFilesDir(),"library"), oldLibBackup=new File(c.getCacheDir(),"hse_old_lib_"+System.nanoTime());
        boolean oldDbMoved=false, oldLibMoved=false, committed=false;
        try {
            extract(zip,tmp);
            File dbf=new File(tmp,"hse.db"); if(!dbf.isFile()) throw new IOException("database missing");
            copy(dbf,staged); if(staged.length()>MAX_ENTRY_BYTES) throw new IOException("دیتابیس بازیابی‌شده بیش از حد مجاز است"); validateDatabase(staged);
            File src=new File(tmp,"library"); if(src.exists()&&!src.isDirectory()) throw new IOException("کتابخانه Backup نامعتبر است");
            if(oldDb.exists()){if(!oldDb.renameTo(oldDbBackup)) throw new IOException("پشتیبان موقت دیتابیس فعلی ناموفق بود");oldDbMoved=true;}
            delete(new File(oldDb.getPath()+"-wal")); delete(new File(oldDb.getPath()+"-shm"));
            if(oldLib.exists()){if(Files.isSymbolicLink(oldLib.toPath())) throw new IOException("کتابخانه فعلی نامعتبر است");if(!oldLib.renameTo(oldLibBackup)) throw new IOException("پشتیبان موقت کتابخانه فعلی ناموفق بود");oldLibMoved=true;}
            if(!staged.renameTo(oldDb)){copy(staged,oldDb);if(!oldDb.isFile())throw new IOException("ثبت دیتابیس بازیابی‌شده ناموفق بود");}
            if(src.exists())copyDir(src,oldLib);
            LibraryDb verify=new LibraryDb(c); boolean healthy; try{healthy=verify.integrityOk();}finally{verify.close();} if(!healthy)throw new IOException("سلامت دیتابیس بازیابی‌شده تأیید نشد");
            delete(oldDbBackup);delete(oldLibBackup);committed=true;
        } finally {
            boolean dbRollbackOk=true, libRollbackOk=true;
            if(!committed){
                if(oldDb.exists() && !oldDb.delete()) dbRollbackOk=false;
                delete(new File(oldDb.getPath()+"-wal")); delete(new File(oldDb.getPath()+"-shm"));
                if(oldDbMoved && oldDbBackup.exists()){
                    dbRollbackOk=restoreMovedFile(oldDbBackup,oldDb);
                }
                if(oldLib.exists() && oldLibMoved) delete(oldLib);
                if(oldLibMoved && oldLibBackup.exists()){
                    libRollbackOk=restoreMovedDirectory(oldLibBackup,oldLib);
                }
                if(!dbRollbackOk || !libRollbackOk){
                    // Never delete the only remaining recovery copy when rollback itself failed.
                    throw new IOException("بازیابی ناموفق بود و نسخه موقت بازیابی خودکار نشد؛ فایل پشتیبان موقت در cache نگه داشته شد");
                }
            }
            delete(tmp); if(staged.exists())staged.delete();
            if(committed || dbRollbackOk) delete(oldDbBackup);
            if(committed || libRollbackOk) delete(oldLibBackup);
        }
    }
    private static boolean restoreMovedFile(File backup,File target){
        if(!backup.exists()) return false;
        if(backup.renameTo(target) && target.isFile()) return true;
        try { copy(backup,target); return target.isFile(); } catch(Exception ignored) { return false; }
    }
    private static boolean restoreMovedDirectory(File backup,File target){
        if(!backup.exists()) return false;
        if(backup.renameTo(target) && target.isDirectory()) return true;
        try { copyDir(backup,target); return target.isDirectory(); } catch(Exception ignored) { return false; }
    }
    private static void extract(File zip,File tmp)throws Exception {
        long total=0; int entries=0; Set<String> seen=new HashSet<>(); File base=tmp.getCanonicalFile();
        try(ZipInputStream in=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))){
            ZipEntry e; byte[] b=new byte[32768];
            while((e=in.getNextEntry())!=null){
                if(++entries>MAX_ENTRIES) throw new IOException("تعداد فایل‌های Backup بیش از حد مجاز است");
                String n=e.getName(); if(n==null||n.isEmpty()||n.length()>MAX_NAME||n.indexOf('\0')>=0||!seen.add(n)) throw new IOException("ورودی تکراری/نامعتبر در Backup");
                if(e.isDirectory()){if(!validDir(n))throw new IOException("مسیر پوشه نامعتبر است");continue;}
                if(!validFile(n))throw new IOException("فایل Backup نامعتبر است");
                File f=new File(tmp,n), canonical=f.getCanonicalFile(); if(!canonical.getPath().startsWith(base.getPath()+File.separator))throw new IOException("مسیر ناامن در Backup");
                File parent=f.getParentFile(); if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("ساخت مسیر بازیابی ناموفق بود");
                long entry=0; try(OutputStream out=new BufferedOutputStream(new FileOutputStream(f))){int k;while((k=in.read(b))!=-1){entry+=k;total+=k;if(entry>MAX_ENTRY_BYTES||total>MAX_BACKUP_BYTES)throw new IOException("حجم Backup بیش از حد مجاز است");out.write(b,0,k);}}
            }
        }
    }
    private static boolean validDir(String n){return n.startsWith("library/")&&!n.startsWith("/")&&!n.contains("..")&&!n.contains("\\");}
    private static boolean validFile(String n){return "hse.db".equals(n)||(n.startsWith("library/")&&!n.startsWith("/")&&!n.contains("..")&&!n.contains("\\"));}
    private static void validateDatabase(File f)throws Exception {
        SQLiteDatabase d=SQLiteDatabase.openDatabase(f.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try{
            Cursor c=d.rawQuery("PRAGMA integrity_check",null); boolean ok=c.moveToFirst()&&"ok".equalsIgnoreCase(c.getString(0)); c.close(); if(!ok)throw new IOException("دیتابیس Backup خراب است");
            Cursor fk=d.rawQuery("PRAGMA foreign_key_check",null); try { if(fk.moveToFirst()) throw new IOException("ارتباطات دیتابیس Backup نامعتبر است"); } finally { fk.close(); }
            Cursor v=d.rawQuery("PRAGMA user_version",null);int ver=v.moveToFirst()?v.getInt(0):0;v.close();if(ver<DB_VERSION)throw new IOException("نسخه دیتابیس Backup قدیمی است"); if(ver>DB_VERSION)throw new IOException("نسخه دیتابیس Backup با این برنامه سازگار نیست");
            requireColumns(d,"documents",new String[]{"id","title","filepath","file_hash","created_at","title_normalized","category_normalized"}); requireColumns(d,"pages",new String[]{"id","document_id","page_no","text","normalized"});
            requireColumns(d,"notes",new String[]{"id","title","body","updated_at"}); requireColumns(d,"reminders",new String[]{"id","title","body","at_millis","done"});
            requireColumns(d,"risks",new String[]{"id","hazard","likelihood","severity","score","status"}); requireColumns(d,"incidents",new String[]{"id","title","event_date","status"});
        } finally {d.close();}
    }
    private static void requireColumns(SQLiteDatabase d,String table,String[] req)throws Exception{Set<String> have=new HashSet<>();Cursor c=d.rawQuery("PRAGMA table_info("+table+")",null);try{while(c.moveToNext())have.add(c.getString(1));}finally{c.close();}for(String x:req)if(!have.contains(x))throw new IOException("ستون "+x+" در جدول "+table+" وجود ندارد");}
    private static void copy(File a,File b)throws Exception{File parent=b.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("ساخت مقصد ناموفق بود");try(InputStream i=new BufferedInputStream(new FileInputStream(a));OutputStream o=new BufferedOutputStream(new FileOutputStream(b))){byte[]x=new byte[32768];int n;while((n=i.read(x))!=-1)o.write(x,0,n);}}
    private static void copyDir(File a,File b)throws Exception{if(Files.isSymbolicLink(a.toPath()))throw new IOException("Symlink مجاز نیست");if(!b.exists()&&!b.mkdirs())throw new IOException("ساخت کتابخانه ناموفق بود");File[]fs=a.listFiles();if(fs==null)throw new IOException("خواندن کتابخانه Backup ناموفق بود");for(File f:fs){if(Files.isSymbolicLink(f.toPath()))throw new IOException("Symlink مجاز نیست");File d=new File(b,f.getName());if(f.isDirectory())copyDir(f,d);else copy(f,d);}}
    private static void delete(File f){if(!f.exists())return;if(f.isDirectory()){File[]fs=f.listFiles();if(fs!=null)for(File x:fs)delete(x);}f.delete();}
}
