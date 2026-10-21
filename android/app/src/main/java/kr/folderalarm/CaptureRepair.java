package kr.folderalarm;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.util.*;

final class CaptureRepair {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("watch",0);}
    static JSONObject session(Context c){try{return new JSONObject(prefs(c).getString("repairSession","{}"));}catch(Exception e){return new JSONObject();}}
    static boolean active(Context c,String folder,String book){JSONObject s=session(c);return s.optBoolean("active")&&folder.equals(s.optString("folder"))&&book.equals(s.optString("book"));}
    static String label(Context c){JSONObject s=session(c);return s.optBoolean("active")?" · 구간 재캡처 "+s.optJSONArray("shots").length()+"장":"";}
    static String stagedName(String original){int dot=original.lastIndexOf('.');return "__recap_"+UUID.randomUUID().toString().replace("-","")+(dot<0?".png":original.substring(dot));}
    static void begin(Context c,String folder,String book)throws Exception{
        if(!prefs(c).getString("repairJournal","").isEmpty())throw new IllegalStateException("중단된 적용을 먼저 복구하세요");
        JSONObject existing=session(c);
        if(!existing.optString("folder").isEmpty()){
            if(folder.equals(existing.optString("folder"))&&book.equals(existing.optString("book"))){existing.put("active",true);saveSession(c,existing);return;}
            throw new IllegalStateException("기존 재캡처 묶음을 먼저 분석하거나 취소·보관하세요");
        }
        JSONObject s=new JSONObject().put("folder",folder).put("book",book).put("active",true).put("startPage",PageStart.selected(c,folder,book)).put("step",CaptureMode.step(c,book)).put("shots",new JSONArray());saveSession(c,s);
    }
    static void saveSession(Context c,JSONObject s)throws Exception{if(!prefs(c).edit().putString("repairSession",s.toString()).commit())throw new java.io.IOException("보정 기록 저장 실패");}
    static Uri stage(Context c,Uri uri,String name,long order)throws Exception{
        JSONObject s=session(c);Uri next=DocumentsContract.renameDocument(c.getContentResolver(),uri,name);if(next==null)throw new java.io.IOException("재캡처 보관 실패");
        JSONArray shots=s.getJSONArray("shots");shots.put(new JSONObject().put("uri",next.toString()).put("name",name).put("order",order));saveSession(c,s);return next;
    }
    static final class Doc{String uri,name;Doc(String u,String n){uri=u;name=n;}}
    static List<Doc> docs(Context c,String folder)throws Exception{
        Uri tree=Uri.parse(folder);List<Doc> result=new ArrayList<>();String[] cols={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        try(Cursor cursor=c.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),cols,null,null,null)){
            if(cursor==null)throw new java.io.IOException("폴더 접근 실패");while(cursor.moveToNext())if(!DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2)))result.add(new Doc(DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)).toString(),cursor.getString(1)));
        }return result;
    }
    static List<RepairPlan.Page> bookPages(Context c,String folder,String book)throws Exception{
        List<Doc> all=docs(c,folder);all.removeIf(d->NamePolicy.number(book,d.name)<1);all.sort(Comparator.comparingLong(d->NamePolicy.number(book,d.name)));
        Set<Long> numbers=new HashSet<>();List<RepairPlan.Page> pages=new ArrayList<>();for(Doc d:all){if(!numbers.add(NamePolicy.number(book,d.name)))throw new IllegalArgumentException("같은 파일 번호가 여러 개 있습니다. 먼저 파일명을 정리하세요");pages.add(new RepairPlan.Page(d.uri,d.name,ImageFingerprint.hash(c,Uri.parse(d.uri))));}return pages;
    }
    static RepairPlan finish(Context c,String folder,String book)throws Exception{
        JSONObject s=session(c);if(!folder.equals(s.optString("folder"))||!book.equals(s.optString("book")))throw new IllegalStateException("현재 책의 재캡처 묶음이 없습니다");
        s.put("active",false);saveSession(c,s);List<RepairPlan.Page> shots=new ArrayList<>();JSONArray array=s.getJSONArray("shots");
        List<JSONObject> ordered=new ArrayList<>();for(int i=0;i<array.length();i++)ordered.add(array.getJSONObject(i));ordered.sort(Comparator.comparingLong(a->a.optLong("order",0)));
        for(JSONObject a:ordered)shots.add(new RepairPlan.Page(a.getString("uri"),a.getString("name"),ImageFingerprint.hash(c,Uri.parse(a.getString("uri")))));
        long start=s.optLong("startPage",0);
        if(start>0){Map<String,Long> trusted=new HashMap<>();JSONObject saved=new JSONObject(prefs(c).getString("pageAnchors:"+folder+":"+book,"{}"));Iterator<String> keys=saved.keys();while(keys.hasNext()){String key=keys.next();trusted.put(key,saved.getLong(key));}return RepairPlan.anchored(bookPages(c,folder,book),shots,book,start,trusted,s.optInt("step",1));}
        return RepairPlan.align(bookPages(c,folder,book),shots,s.optInt("step",1));
    }
    static void cancel(Context c){JSONObject s=session(c);prefs(c).edit().remove(PageStart.key(s.optString("folder"),s.optString("book"))).remove("repairSession").commit();}
    static String target(String book,long number,String original){int dot=original.lastIndexOf('.');return book+"_"+String.format(Locale.ROOT,"%03d",number)+(dot<0?".png":original.substring(dot));}
    static void journal(Context c,JSONArray records)throws Exception{if(!prefs(c).edit().putString("repairJournal",records.toString()).commit())throw new java.io.IOException("복구 기록 저장 실패");}
    static String rename(Context c,String uri,String name)throws Exception{Uri next=DocumentsContract.renameDocument(c.getContentResolver(),Uri.parse(uri),name);if(next==null)throw new java.io.IOException("이름 변경 실패");return next.toString();}
    static void rebaseline()throws Exception{if(WatchService.live!=null)WatchService.live.resetBaseline();}
    static void apply(Context c,String folder,String book,RepairPlan plan)throws Exception{
        if(!prefs(c).getString("repairJournal","").isEmpty())throw new IllegalStateException("중단된 적용을 먼저 복구하세요");
        if(!plan.staged.isEmpty()){
            JSONObject s=session(c);JSONArray current=s.optJSONArray("shots");
            if(s.optBoolean("active")||current==null||current.length()!=plan.staged.size())throw new IllegalStateException("분석 후 새 캡처가 추가되었습니다. 다시 종료·분석하세요");
        }
        List<RepairPlan.Page> fresh=bookPages(c,folder,book);if(fresh.size()!=plan.before.size())throw new IllegalStateException("기존 파일이 달라졌습니다. 다시 분석하세요");
        for(int i=0;i<fresh.size();i++){RepairPlan.Page a=fresh.get(i),b=plan.before.get(i);if(!a.uri.equals(b.uri)||!a.hash.equals(b.hash)||!a.name.equals(b.name))throw new IllegalStateException("기존 파일이 달라졌습니다. 다시 분석하세요");}
        for(RepairPlan.Page page:plan.staged)if(!ImageFingerprint.hash(c,Uri.parse(page.uri)).equals(page.hash))throw new IllegalStateException("재캡처 파일이 달라졌습니다");
        Map<String,String> desired=new HashMap<>();for(int i=0;i<plan.after.size();i++){RepairPlan.Page p=plan.after.get(i);desired.put(p.uri,target(book,plan.numbers.get(i),p.name));}
        List<RepairPlan.Page> all=new ArrayList<>(plan.before);all.addAll(plan.staged);JSONArray records=new JSONArray();Set<String> included=new HashSet<>();String tag=UUID.randomUUID().toString().substring(0,8);
        for(RepairPlan.Page p:all){if(!included.add(p.uri)||p.name.equals(desired.get(p.uri)))continue;records.put(new JSONObject().put("uri",p.uri).put("before",p.name).put("current",p.name).put("hash",p.hash).put("temporary","__backup_"+tag+"_"+records.length()+"_"+p.name).put("desired",desired.containsKey(p.uri)?desired.get(p.uri):""));}
        JSONObject metadata=new JSONObject().put("anchorKey","pageAnchors:"+folder+":"+book).put("anchorValue",prefs(c).getString("pageAnchors:"+folder+":"+book,"{}")).put("cursorKey",PageStart.key(folder,book)).put("cursorValue",PageStart.selected(c,folder,book)).put("session",prefs(c).getString("repairSession",""));
        if(!prefs(c).edit().putString("repairJournalMeta",metadata.toString()).putString("repairJournalFolder",folder).commit())throw new java.io.IOException("복구 폴더 기록 저장 실패");
        journal(c,records);
        try{
            Set<String> names=new HashSet<>();for(Doc d:docs(c,folder))names.add(d.name);
            for(int i=0;i<records.length();i++){JSONObject record=records.getJSONObject(i);move(c,record,record.getString("temporary"),names);journal(c,records);}
            for(int i=0;i<records.length();i++){JSONObject record=records.getJSONObject(i);if(!record.getString("desired").isEmpty()){move(c,record,record.getString("desired"),names);journal(c,records);}}
            SharedPreferences.Editor edit=prefs(c).edit().putString("lastRepairJournal",records.toString()).remove("repairJournal").remove("repairSession");
            if(!plan.anchors.isEmpty()){
                String key="pageAnchors:"+folder+":"+book;JSONObject saved=new JSONObject(prefs(c).getString(key,"{}"));long last=0;
                for(Map.Entry<String,Long> entry:plan.anchors.entrySet()){saved.put(entry.getKey(),entry.getValue());last=Math.max(last,entry.getValue());}edit.putString(key,saved.toString()).putLong(PageStart.key(folder,book),PageStride.advance(last,1,plan.step));
            }
            rebaseline();if(!edit.remove("repairJournalMeta").commit())throw new java.io.IOException("페이지 기준 저장 실패");
        }catch(Exception e){try{rollback(c,folder,records);}catch(Exception recovery){throw new java.io.IOException("적용 중단. 파일은 보존되어 있습니다. ‘중단 복구’를 누르세요: "+recovery.getMessage());}throw e;}
    }
    static void move(Context c,JSONObject record,String name,Set<String> names)throws Exception{
        if(names.contains(name))throw new java.io.IOException("같은 이름이 있어 중단: "+name);
        String previous=record.getString("current");String uri=rename(c,record.getString("uri"),name);record.put("uri",uri).put("current",name);names.remove(previous);names.add(name);
        try(Cursor cursor=c.getContentResolver().query(Uri.parse(uri),new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(cursor==null||!cursor.moveToFirst()||!name.equals(cursor.getString(0)))throw new java.io.IOException("저장소가 요청한 파일명을 적용하지 않았습니다");}
    }
    static void rollback(Context c,String folder,JSONArray records)throws Exception{
        if(!folder.equals(prefs(c).getString("repairJournalFolder",folder)))throw new IllegalStateException("중단된 작업을 진행했던 원래 폴더를 선택한 뒤 복구하세요");
        List<Doc> all=docs(c,folder);Set<String> names=new HashSet<>();for(Doc d:all)names.add(d.name);
        // Resolve every source by content before moving any file during recovery.
        for(int i=0;i<records.length();i++){
            JSONObject r=records.getJSONObject(i);Doc resolved=null;
            for(Doc d:all)if(d.uri.equals(r.getString("uri"))&&ImageFingerprint.hash(c,Uri.parse(d.uri)).equals(r.getString("hash"))){resolved=d;break;}
            if(resolved!=null){r.put("current",resolved.name);continue;}
            for(Doc d:all)if(d.uri.equals(r.getString("uri"))||d.name.equals(r.getString("current"))||d.name.equals(r.getString("temporary"))||d.name.equals(r.getString("before"))||d.name.equals(r.getString("desired"))){
                if(ImageFingerprint.hash(c,Uri.parse(d.uri)).equals(r.getString("hash"))){if(resolved!=null&&!resolved.uri.equals(d.uri))throw new java.io.IOException("복구 대상이 여러 개라 자동 복구를 멈췄습니다");resolved=d;}}
            if(resolved==null)throw new java.io.IOException("복구 파일을 찾지 못했습니다");r.put("uri",resolved.uri).put("current",resolved.name);
        }
        String tag=UUID.randomUUID().toString();
        for(int i=0;i<records.length();i++){move(c,records.getJSONObject(i),"__restore_"+tag+"_"+i,names);journal(c,records);}
        for(int i=0;i<records.length();i++){move(c,records.getJSONObject(i),records.getJSONObject(i).getString("before"),names);journal(c,records);}
        SharedPreferences.Editor undo=prefs(c).edit().remove("repairJournal").remove("repairJournalMeta");
        String text=prefs(c).getString("repairJournalMeta","");if(!text.isEmpty()){
            JSONObject metadata=new JSONObject(text);undo.putString(metadata.getString("anchorKey"),metadata.getString("anchorValue"));
            if(metadata.optLong("cursorValue",0)>0)undo.putLong(metadata.getString("cursorKey"),metadata.getLong("cursorValue"));else undo.remove(metadata.getString("cursorKey"));
            if(metadata.optString("session").isEmpty())undo.remove("repairSession");else undo.putString("repairSession",metadata.getString("session"));
        }
        if(!undo.commit())throw new java.io.IOException("페이지 기준 복구 기록 저장 실패");rebaseline();
    }
    static void recover(Context c,String folder)throws Exception{String text=prefs(c).getString("repairJournal","");if(text.isEmpty())throw new IllegalStateException("중단된 적용 기록이 없습니다");rollback(c,folder,new JSONArray(text));}
}
