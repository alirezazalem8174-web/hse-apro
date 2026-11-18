from pathlib import Path
import re, zipfile, sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def ok(name): print('PASS',name)
def fail(name,msg): errors.append(f'{name}: {msg}'); print('FAIL',name,msg)
# Required files
required=['settings.gradle','build.gradle','gradle.properties','gradle/wrapper/gradle-wrapper.properties','app/build.gradle','app/src/main/AndroidManifest.xml']
for x in required:
    (ok('required '+x) if (ROOT/x).is_file() else fail('required '+x,'missing'))
# Version consistency
b=(ROOT/'app/build.gradle').read_text(); m=re.search(r'versionCode\s+(\d+)',b); n=re.search(r"versionName\s+'([^']+)'",b)
if m and m.group(1)=='56': ok('versionCode 56')
else: fail('versionCode','not 56')
if n and n.group(1)=='4.6.1': ok('versionName 4.6.1')
else: fail('versionName','not 4.6.1')
# Manifest essentials
man=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
for needle in ['POST_NOTIFICATIONS','SCHEDULE_EXACT_ALARM','RECEIVE_BOOT_COMPLETED','android:exported="true"','android:allowBackup="false"']:
    (ok('manifest '+needle) if needle in man else fail('manifest '+needle,'missing'))
# Java balance and dangerous patterns
for p in (ROOT/'app/src/main/java').rglob('*.java'):
    s=p.read_text()
    if s.count('{')!=s.count('}') or s.count('(')!=s.count(')'): fail('balance '+p.name,'delimiter mismatch')
    else: ok('balance '+p.name)
    for bad in ['printStackTrace()', 'System.out.', 'TODO', 'FIXME']:
        if bad in s: fail('code hygiene '+p.name,bad)
# Package hygiene and resource syntax checks.
import xml.etree.ElementTree as ET
for bad_dir in ROOT.rglob('__pycache__'):
    fail('package hygiene', f'generated cache directory included: {bad_dir.relative_to(ROOT)}')
for xml_file in ROOT.rglob('*.xml'):
    try:
        ET.parse(xml_file)
    except Exception as exc:
        fail('XML parse', f'{xml_file.relative_to(ROOT)}: {exc}')

# SQLite safety: PRAGMA statements that return rows must never use execSQL.
for p in (ROOT/'app/src/main/java').rglob('*.java'):
    s=p.read_text()
    if re.search(r'\.execSQL\(\s*[\"\']PRAGMA\b', s, re.I):
        fail('sqlite PRAGMA '+p.name,'query-like PRAGMA must use rawQuery')
    else: ok('sqlite PRAGMA '+p.name)

# Regression for the reported Android SQLite error: query-like PRAGMA must be executed via rawQuery, not execSQL.
ldb=(ROOT/'app/src/main/java/com/ekbatan/hse/LibraryDb.java').read_text()
if 'execSQL("PRAGMA busy_timeout' in ldb: fail('sqlite reported-error regression','busy_timeout still uses execSQL')
else: ok('sqlite reported-error regression')

# Required classes
for cls in ['BackupManager.java','LibraryDb.java','PdfImporter.java','ReminderScheduler.java','ReminderReceiver.java','BootReceiver.java','MainActivity.java','PersianNormalizer.java']:
    if (ROOT/'app/src/main/java/com/ekbatan/hse'/cls).is_file(): ok('class '+cls)
    else: fail('class '+cls,'missing')
# Backup hardening signatures
bm=(ROOT/'app/src/main/java/com/ekbatan/hse/BackupManager.java').read_text()
for needle in ['MAX_BACKUP_BYTES','MAX_ENTRY_BYTES','MAX_ENTRIES','getCanonicalFile','validateDatabase','PRAGMA user_version','requireColumns','title_normalized','category_normalized','Symlink']:
    (ok('backup '+needle) if needle in bm else fail('backup '+needle,'missing'))
# Reminder hardening
rs=(ROOT/'app/src/main/java/com/ekbatan/hse/ReminderScheduler.java').read_text(); rr=(ROOT/'app/src/main/java/com/ekbatan/hse/ReminderReceiver.java').read_text(); br=(ROOT/'app/src/main/java/com/ekbatan/hse/BootReceiver.java').read_text()
for txt,needles,prefix in [(rs,['canScheduleExactAlarms','setExactAndAllowWhileIdle','setAndAllowWhileIdle'],'scheduler'),(rr,['getReminder','areNotificationsEnabled','setReminderDone'],'receiver'),(br,['goAsync','pending.finish'],'boot')]:
    for needle in needles: (ok(prefix+' '+needle) if needle in txt else fail(prefix+' '+needle,'missing'))
# Search escaping
lib=(ROOT/'app/src/main/java/com/ekbatan/hse/LibraryDb.java').read_text()
for needle in ['escapeLike','ESCAPE','LIMIT 120','foreign_key_check','title_normalized','category_normalized','backfillNormalizedDocumentText']:
    (ok('search '+needle) if needle in lib else fail('search '+needle,'missing'))
# Workflow single and version
wf=list((ROOT/'.github/workflows').glob('*.yml'))
if len(wf)==1: ok('single build workflow')
else: fail('single build workflow',f'{len(wf)} workflows')
if wf:
    ws=wf[0].read_text()
    for needle in ['gradle-version: \'8.7\'','assembleDebug','upload-artifact']:
        (ok('workflow '+needle) if needle in ws else fail('workflow '+needle,'missing'))
# Old active version refs outside historical changelogs
for p in ROOT.rglob('*'):
    if p.is_file() and p.suffix in {'.java','.gradle','.yml','.xml','.md','.sh'} and 'docs/CHANGELOG' not in str(p):
        s=p.read_text(errors='ignore')
        if any(x in s for x in ['4.4.0','4.5.0','4.5.1','4.5.2','4.5.3','4.5.4','4.5.5','4.5.6','4.5.8','4.5.9','4.6.0','versionCode 44']): fail('stale version '+str(p.relative_to(ROOT)),'4.4 reference')
        
# Content/UI contract checks
main=(ROOT/'app/src/main/java/com/ekbatan/hse/MainActivity.java').read_text()
book_count=len(re.findall(r'\{\"[^\"]+\",\"[^\"]+\",\"[^\"]+\",\"[^\"]+\"\}', main.split('static final String[][] RECOMMENDED_BOOKS=',1)[1].split('};',1)[0])) if 'static final String[][] RECOMMENDED_BOOKS=' in main else 0
if book_count==10: ok('recommended books count 10')
else: fail('recommended books count',f'expected 10, got {book_count}')
gloss_section=main.split('static final String[][] GLOSSARY=',1)[1].split('};',1)[0] if 'static final String[][] GLOSSARY=' in main else ''
gloss_terms=re.findall(r'\{\"([^\"]+)\",\"', gloss_section)
if len(gloss_terms)==len(set(gloss_terms)): ok('glossary abbreviations unique')
else: fail('glossary abbreviations unique','duplicate abbreviation detected')
for needle in ['changeFontScale','font_scale','LAYOUT_DIRECTION_RTL','addExportTools','buildRecommendedBooks']:
    (ok('UI contract '+needle) if needle in main else fail('UI contract '+needle,'missing'))

# Memory/data amplification hardening
pdf=(ROOT/"app/src/main/java/com/ekbatan/hse/PdfImporter.java").read_text()
for needle in ["MAX_PAGE_TEXT_CHARS","MAX_TOTAL_TEXT_CHARS","حجم کل متن استخراج‌شده"]:
    (ok("pdf text bound "+needle) if needle in pdf else fail("pdf text bound "+needle,"missing"))
lib=(ROOT/"app/src/main/java/com/ekbatan/hse/LibraryDb.java").read_text()
needles=["MAX_NOTE_CHARS", 'split("' + "\\\\" + 's+")']
for needle in needles:
    (ok("input bound "+needle) if needle in lib else fail("input bound "+needle,"missing"))

print('\nTOTAL', 'PASS' if not errors else 'FAIL', 'errors=',len(errors))
if errors:
    print('\n'.join(errors)); sys.exit(1)
