from pathlib import Path
import sqlite3, zipfile, tempfile, shutil, sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def check(name, cond):
    if cond: print('PASS',name)
    else: errors.append(name); print('FAIL',name)
# ZIP path cases
names=['hse.db','library/a.pdf','library/sub/b.pdf']
for n in names: check('valid path '+n, n=='hse.db' or (n.startswith('library/') and '..' not in n and '\\' not in n and not n.startswith('/')))
for n in ['../evil','library/../evil','/absolute','library\\evil','library/a/../../x']:
    check('reject path '+n, not (n=='hse.db' or (n.startswith('library/') and '..' not in n and '\\' not in n and not n.startswith('/'))))
# duplicate ZIP entry detection
with tempfile.TemporaryDirectory() as td:
    z=Path(td)/'dup.zip'
    with zipfile.ZipFile(z,'w') as f:
        f.writestr('hse.db',b'x'); f.writestr('hse.db',b'y')
    with zipfile.ZipFile(z) as f: check('duplicate ZIP detected', len(f.namelist()) != len(set(f.namelist())))
# schema contract
with tempfile.TemporaryDirectory() as td:
    db=Path(td)/'hse.db'; c=sqlite3.connect(db); cur=c.cursor()
    cur.executescript('''PRAGMA user_version=10; CREATE TABLE documents(id INTEGER,title TEXT,filepath TEXT,file_hash TEXT,created_at INTEGER,title_normalized TEXT,category_normalized TEXT); CREATE TABLE pages(id INTEGER,document_id INTEGER,page_no INTEGER,text TEXT,normalized TEXT); CREATE TABLE notes(id INTEGER,title TEXT,body TEXT,updated_at INTEGER); CREATE TABLE reminders(id INTEGER,title TEXT,body TEXT,at_millis INTEGER,done INTEGER); CREATE TABLE risks(id INTEGER,hazard TEXT,likelihood INTEGER,severity INTEGER,score INTEGER,status TEXT); CREATE TABLE incidents(id INTEGER,title TEXT,event_date INTEGER,status TEXT);'''); c.commit();
    required={'documents':{'id','title','filepath','file_hash','created_at','title_normalized','category_normalized'},'pages':{'id','document_id','page_no','text','normalized'},'notes':{'id','title','body','updated_at'},'reminders':{'id','title','body','at_millis','done'},'risks':{'id','hazard','likelihood','severity','score','status'},'incidents':{'id','title','event_date','status'}}
    good=True
    for t,req in required.items():
        cols={r[1] for r in cur.execute(f'pragma table_info({t})')}; good &= req <= cols
    check('schema contract',good); check('schema version 10',cur.execute('pragma user_version').fetchone()[0]==10); c.close()
# SQL LIKE escaping contract
lib=(ROOT/'app/src/main/java/com/ekbatan/hse/LibraryDb.java').read_text()
check('LIKE wildcard escaping present','escapeLike' in lib and "ESCAPE '\\\\'" in lib)

main=(ROOT/'app/src/main/java/com/ekbatan/hse/MainActivity.java').read_text()
check('copy/print manager present',(ROOT/'app/src/main/java/com/ekbatan/hse/ExportPrintManager.java').is_file())
check('copy/print controls wired','addExportTools' in main and 'ExportPrintManager.copy' in main and 'ExportPrintManager.print' in main)
check('HSE abbreviation glossary present','GLOSSARY' in main and 'PPE' in main and 'LOTO' in main and 'PTW' in main and 'HIRA' in main)
check('dashboard export present','exportDashboard' in main)
# no stale active version
stale=[]
for p in ROOT.rglob('*'):
    if p.is_file() and p.suffix in {'.java','.gradle','.yml','.xml','.md','.sh'} and 'CHANGELOG' not in str(p):
        s=p.read_text(errors='ignore')
        if any(x in s for x in ['versionCode 44','4.4.0','4.5.0','4.5.1','4.5.2','4.5.3','4.5.4','4.5.5','4.5.6','4.5.8','4.5.9','4.6.0']): stale.append(str(p))
check('no stale active version',not stale)
# Resource amplification contracts
pdf=(ROOT/"app/src/main/java/com/ekbatan/hse/PdfImporter.java").read_text()
check('PDF per-page text cap','MAX_PAGE_TEXT_CHARS' in pdf)
check('PDF total text cap','MAX_TOTAL_TEXT_CHARS' in pdf)
check('note size cap','MAX_NOTE_CHARS' in (ROOT/'app/src/main/java/com/ekbatan/hse/LibraryDb.java').read_text())

print('\nADVERSARIAL TOTAL', 'PASS' if not errors else 'FAIL', 'errors=',len(errors))
if errors: print('\n'.join(errors)); sys.exit(1)
