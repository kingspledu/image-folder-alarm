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
    String bookName;
    ScheduledExecutorService worker;
    NotificationManager manager;
    android.content.SharedPreferences prefs;
    Set<String> known;
    final Map<String, Long> sizes = new HashMap<>();
    final Set<String> pending = new HashSet<>();
    int previous;
    volatile boolean stopping;
    String treeString;
    static class Image { String id, name; long size; int flags; Image(String i, String n, long s, int f) { id=i; name=n; size=s; flags=f; } }
    Notification.Builder note(String channel, String title, String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_menu_gallery).setContentTitle(title).setContentText(text).setContentIntent(open);
    }
    public void onCreate() {
        super.onCreate(); prefs=getSharedPreferences("watch",0); manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("watch", "감시 상태", NotificationManager.IMPORTANCE_LOW));
        Alerts.channels(this);
    }
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (worker != null) return START_NOT_STICKY;
        bookName=intent==null?prefs.getString("bookName",""):intent.getStringExtra("bookName");
        if(!NamePolicy.validBook(bookName)) { stopSelf();return START_NOT_STICKY; }
        treeString=prefs.getString("uri", "");
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,WatchService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,note("watch","폴더 감시 및 자동 이름 변경 중","새 이미지에 책 이름과 순번을 붙입니다")
            .setOngoing(true).addAction(new Notification.Action.Builder(null,"중지",stop).build()).build());
        running=true;
        worker=Executors.newSingleThreadScheduledExecutor(); worker.scheduleWithFixedDelay(this::scan,0,3,TimeUnit.SECONDS);
        return START_NOT_STICKY;
    }
    List<Image> list(Uri tree) throws Exception {
        List<Image> result=new ArrayList<>();
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS};
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try(Cursor c=getContentResolver().query(children,columns,null,null,null)) {
            if(c==null) throw new IllegalStateException("폴더를 읽지 못했습니다");
            while(c.moveToNext()) {
                String name=c.getString(1),mime=c.getString(2);
                if(mime!=null && mime.startsWith("image/") || !DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) && name!=null && name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|gif|bmp|heic|heif|avif)$"))
                    result.add(new Image(c.getString(0),name,c.isNull(3)?-1:c.getLong(3),c.getInt(4)));
            }
        }
        result.sort(Comparator.comparing(i -> i.name)); return result;
    }
    void scan() { synchronized(FILE_LOCK) { if(!stopping) scanLocked(); } }
    void scanLocked() {
        try {
            Uri tree=Uri.parse(treeString); List<Image> images=list(tree);
            Set<String> ids=new HashSet<>(), names=new HashSet<>();
            for(Image image:images) { ids.add(image.id); names.add(image.name); }
            if(known==null) { known=ids; previous=images.size(); status("감시 시작 · 기존 이미지 "+previous+"장은 유지"); return; }
            int added=Math.max(0,images.size()-previous), renamed=0, failures=0;
            for(Image image:images) if(!known.contains(image.id)) pending.add(image.id);
            pending.retainAll(ids); sizes.keySet().retainAll(ids);
            for(Image image:images) {
                if(stopping) return;
                if(!pending.contains(image.id)) continue;
                Long last=sizes.put(image.id,image.size);
                // Two equal nonzero sizes on consecutive scans avoid renaming while a capture is being written.
                if(last==null || image.size<=0 || last.longValue()!=image.size) continue;
                try {
                    if((image.flags & DocumentsContract.Document.FLAG_SUPPORTS_RENAME)==0) throw new IllegalStateException("이름 변경 미지원");
                    String next=NamePolicy.next(bookName,image.name,names);
                    Uri old=DocumentsContract.buildDocumentUriUsingTree(tree,image.id);
                    Uri updated=DocumentsContract.renameDocument(getContentResolver(),old,next);
                    if(updated==null) throw new IllegalStateException("이름 변경 실패");
                    ids.remove(image.id); ids.add(DocumentsContract.getDocumentId(updated)); names.add(next); renamed++;
                } catch(Exception e) { failures++; }
                pending.remove(image.id); sizes.remove(image.id);
            }
            known=ids;previous=images.size();
            String text="전체 "+previous+"장 · 이름 변경 "+renamed+"장"+(failures>0?" · 변경 실패 "+failures+"장 (원본 유지)":"");
            if(added>0) {
                String event="최근 감지: "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(new Date())+" · 새 이미지 "+added+"장 · 전체 "+previous+"장";
                prefs.edit().putString("lastEvent",event).apply();
                manager.notify(2,note("images","새 이미지 "+added+"장 감지",event).setAutoCancel(true).build());
            }
            // Renaming results must not replace the capture alert a few seconds later.
            if(renamed>0 || failures>0) manager.notify(5,note("results","자동 이름 변경 결과",text).setAutoCancel(true).build());
            status("감시 중 · "+text+(pending.isEmpty()?"":" · 저장 완료 대기 "+pending.size()+"장"));
        } catch(Exception e) {
            status("폴더 접근 오류 · 다시 선택하세요");
            manager.notify(3,note("images","감시 중단","폴더 접근 권한을 확인하고 다시 시작하세요").build()); stopSelf();
        }
    }
    void status(String text) { if(!stopping)prefs.edit().putString("status",text).apply(); }
    public void onDestroy() { stopping=true;running=false;if(worker!=null)worker.shutdownNow();prefs.edit().putString("status","감시 중지").apply();super.onDestroy(); }
    public IBinder onBind(Intent intent) { return null; }
}
