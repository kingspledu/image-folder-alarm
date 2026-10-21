package kr.folderalarm;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.DocumentsContract;
import java.util.*;
import java.util.concurrent.*;

public class WatchService extends Service {
    static final Object FILE_LOCK=new Object();
    static volatile boolean running;
    static WatchService live;
    static long displayNumber(){return live==null?0:live.lastNumber;}
    static int displayPending(){return live==null?0:live.pendingNumber;}
    String bookName;
    FloatingCounter floatingCounter;
    volatile long lastNumber;
    volatile int pendingNumber;
    ScheduledExecutorService worker;
    NotificationManager manager;
    android.content.SharedPreferences prefs;
    Set<String> known;
    final Map<String, Long> sizes = new HashMap<>();
    final Set<String> pending = new HashSet<>();
    final Map<String,String> reserved=new HashMap<>();
    final Map<String,Long> captureOrder=new HashMap<>();
    long captureClock;
    final Map<String,Long> stableSince=new HashMap<>();
    android.database.ContentObserver observer;
    final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
    int previous;
    volatile boolean stopping;
    String treeString;
    static class Image { String id, name; long size,modified; int flags; Image(String i, String n, long s, int f,long m) { id=i; name=n; size=s; flags=f;modified=m; } }
    Notification.Builder note(String channel, String title, String text) {

        return new Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_menu_gallery).setContentTitle(title).setContentText(text);
    }
    public void onCreate() {
        super.onCreate(); prefs=getSharedPreferences("watch",0); manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("watch", "감시 상태", NotificationManager.IMPORTANCE_LOW));
        Alerts.channels(this);
    }
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(intent!=null&&"overlay".equals(intent.getAction())){if(worker!=null)syncFloating();else stopSelf();return START_NOT_STICKY;}
        if (worker != null) return START_NOT_STICKY;
        bookName=intent==null?prefs.getString("bookName",""):intent.getStringExtra("bookName");
        if(!NamePolicy.validBook(bookName)) { stopSelf();return START_NOT_STICKY; }
        treeString=prefs.getString("uri", "");
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,WatchService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,note("watch","폴더 감시 및 자동 이름 변경 중","새 이미지에 책 이름과 순번을 붙입니다")
            .setOngoing(true).addAction(new Notification.Action.Builder(null,"중지",stop).build()).build());
        running=true;live=this;
        syncFloating();
        worker=Executors.newSingleThreadScheduledExecutor(); worker.scheduleWithFixedDelay(this::scan,0,200,TimeUnit.MILLISECONDS);
        observer=new android.database.ContentObserver(null){public void onChange(boolean selfChange){requestScan();}};
        try{
            Uri tree=Uri.parse(treeString);
            getContentResolver().registerContentObserver(DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),true,observer);
        }catch(RuntimeException ignored){ /* 200ms polling remains available if this provider has no observer support. */ }
        return START_NOT_STICKY;
    }
    List<Image> list(Uri tree) throws Exception {
        List<Image> result=new ArrayList<>();
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try(Cursor c=getContentResolver().query(children,columns,null,null,null)) {
            if(c==null) throw new IllegalStateException("폴더를 읽지 못했습니다");
            while(c.moveToNext()) {
                String name=c.getString(1),mime=c.getString(2);
                if(mime!=null && mime.startsWith("image/") || !DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) && name!=null && name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|gif|bmp|heic|heif|avif)$"))
                    result.add(new Image(c.getString(0),name,c.isNull(3)?-1:c.getLong(3),c.getInt(4),c.isNull(5)?0:c.getLong(5)));
            }
        }
        result.sort(Comparator.comparingLong((Image i)->i.modified).thenComparing(i->i.name)); return result;
    }
    void requestScan(){
        ScheduledExecutorService executor=worker;
        if(stopping||executor==null||!queued.compareAndSet(false,true))return;
        try{executor.execute(()->{queued.set(false);scan();});}catch(java.util.concurrent.RejectedExecutionException ignored){queued.set(false);}
    }
    void scan() { synchronized(FILE_LOCK) { if(!stopping) scanLocked(); } }
    void scanLocked() {
        try {
            Uri tree=Uri.parse(treeString); List<Image> images=list(tree);
            Set<String> ids=new HashSet<>(), names=new HashSet<>();
            for(Image image:images) { ids.add(image.id); names.add(image.name); }
            if(known==null) { known=ids; previous=images.size(); updateFloating(names,0); status("감시 시작 · 기존 이미지 "+previous+"장은 유지"); return; }
            int added=Math.max(0,images.size()-previous), renamed=0, failures=0;
            pending.retainAll(ids); sizes.keySet().retainAll(ids);reserved.keySet().retainAll(ids);stableSince.keySet().retainAll(ids);captureOrder.keySet().retainAll(ids);
            Set<String> occupied=new HashSet<>(names);occupied.addAll(reserved.values());
            for(Image image:images) if(!known.contains(image.id)&&pending.add(image.id)){
                if(PageStart.selected(this,treeString,bookName)>0&&!CaptureRepair.active(this,treeString,bookName))CaptureRepair.begin(this,treeString,bookName);
                captureClock=Math.max(captureClock+1,System.currentTimeMillis()*1000);captureOrder.put(image.id,captureClock);
                String target=CaptureRepair.active(this,treeString,bookName)?CaptureRepair.stagedName(image.name):NamePolicy.next(bookName,image.name,occupied,CaptureMode.step(this,bookName));reserved.put(image.id,target);occupied.add(target);
            }
            // Publish allocated numbers before waiting for file stability or provider rename calls.
            Set<String> visibleNames=new HashSet<>(names);visibleNames.addAll(reserved.values());updateFloating(visibleNames,pending.size());
            for(Image image:images) {
                if(stopping) return;
                if(!pending.contains(image.id)) continue;
                Long last=sizes.put(image.id,image.size);
                long now=android.os.SystemClock.uptimeMillis();
                if(last==null||last.longValue()!=image.size){stableSince.put(image.id,now);continue;}
                if(image.size<=0||now-stableSince.getOrDefault(image.id,now)<300)continue;
                try {
                    if((image.flags & DocumentsContract.Document.FLAG_SUPPORTS_RENAME)==0) throw new IllegalStateException("이름 변경 미지원");
                    String next=reserved.get(image.id);
                    if(next==null)continue;
                    if(names.contains(next)&&!image.name.equals(next)){
                        Set<String> taken=new HashSet<>(names);taken.addAll(reserved.values());next=NamePolicy.next(bookName,image.name,taken,CaptureMode.step(this,bookName));reserved.put(image.id,next);
                    }
                    Uri old=DocumentsContract.buildDocumentUriUsingTree(tree,image.id);
                    Uri updated;
                    if(CaptureRepair.active(this,treeString,bookName))updated=CaptureRepair.stage(this,old,CaptureRepair.stagedName(image.name),captureOrder.getOrDefault(image.id,0L));
                    else updated=DocumentsContract.renameDocument(getContentResolver(),old,next);
                    if(updated==null) throw new IllegalStateException("이름 변경 실패");
                    ids.remove(image.id); ids.add(DocumentsContract.getDocumentId(updated)); if(!CaptureRepair.active(this,treeString,bookName))names.add(next); renamed++;
                } catch(Exception e) { failures++; }
                pending.remove(image.id); sizes.remove(image.id);reserved.remove(image.id);stableSince.remove(image.id);captureOrder.remove(image.id);
            }
            known=ids;previous=images.size();
            visibleNames=new HashSet<>(names);visibleNames.addAll(reserved.values());updateFloating(visibleNames,pending.size());
            String text="전체 "+previous+"장 · 이름 변경 "+renamed+"장"+(failures>0?" · 변경 실패 "+failures+"장 (원본 유지)":"");
            if(added>0) {
                String event="최근 감지: "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(new Date())+" · 새 이미지 "+added+"장 · 전체 "+previous+"장";
                prefs.edit().putString("lastEvent",event).apply();
                if(floatingCounter==null||!floatingCounter.visible)manager.notify(2,note("images","새 이미지 "+added+"장 감지",event).setAutoCancel(true).build());
            }
            // Renaming results must not replace the capture alert a few seconds later.
            if(failures>0 || renamed>0&&(floatingCounter==null||!floatingCounter.visible)) manager.notify(5,note("results","자동 이름 변경 결과",text).setAutoCancel(true).build());
            status("감시 중 · "+text+(pending.isEmpty()?"":" · 저장 완료 대기 "+pending.size()+"장"));
        } catch(Exception e) {
            status("폴더 접근 오류 · 다시 선택하세요");
            manager.notify(3,note("images","감시 중단","폴더 접근 권한을 확인하고 다시 시작하세요").build()); stopSelf();
        }
    }
    void resetBaseline()throws Exception{
        List<Image> current=list(Uri.parse(treeString));Set<String> ids=new HashSet<>(),names=new HashSet<>();
        for(Image i:current){
            if(NamePolicy.number(bookName,i.name)>0||i.name.startsWith("__backup_")||i.name.startsWith("__recap_")||i.name.startsWith("__restore_")||known!=null&&known.contains(i.id)&&!pending.contains(i.id))ids.add(i.id);
            names.add(i.name);
        }
        known=ids;previous=current.size();pending.clear();reserved.clear();sizes.clear();stableSince.clear();captureOrder.clear();updateFloating(names,0);
    }
    void syncFloating(){
        if(floatingCounter==null)floatingCounter=new FloatingCounter(this);
        if(prefs.getBoolean("floating",true)&&android.provider.Settings.canDrawOverlays(this)){
            floatingCounter.show(bookName);floatingCounter.update(lastNumber,pendingNumber);
        }else floatingCounter.hide();
    }
    void updateFloating(Set<String> names,int waiting){
        long max=0;for(String name:names)max=Math.max(max,NamePolicy.number(bookName,name));
        lastNumber=max;pendingNumber=waiting;
        FloatingCounter counter=floatingCounter;if(counter!=null)counter.update(max,waiting);
    }
    void status(String text) { if(!stopping)prefs.edit().putString("status",text).apply(); }
    public void onDestroy() { stopping=true;live=null;if(observer!=null)getContentResolver().unregisterContentObserver(observer);if(floatingCounter!=null)floatingCounter.destroy();running=false;if(worker!=null)worker.shutdownNow();prefs.edit().putString("status","감시 중지").apply();super.onDestroy(); }
    public IBinder onBind(Intent intent) { return null; }
}
