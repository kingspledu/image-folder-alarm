package kr.folderalarm;
import android.content.*;
import org.json.*;
final class PageStart {
    static String key(String folder,String book){return "pageStart:"+folder+":"+book;}
    static long selected(Context c,String folder,String book){return CaptureRepair.prefs(c).getLong(key(folder,book),0);}
    static long shown(Context c,String folder,String book,long actual,int waiting){
        JSONObject s=CaptureRepair.session(c);
        if(folder.equals(s.optString("folder"))&&book.equals(s.optString("book"))&&s.optLong("startPage",0)>0)return s.optLong("startPage")+s.optJSONArray("shots").length()+waiting;
        long value=selected(c,folder,book);return value>0?value:actual+1;
    }
    static void select(Context c,String folder,String book,long value)throws Exception{
        if(value<1||value>1000000000L)throw new IllegalArgumentException("시작 페이지는 1~1000000000 사이여야 합니다");
        WatchService w=WatchService.live;if(w==null||w.known==null)throw new IllegalStateException("감시가 준비된 뒤 지정하세요");if(!w.pending.isEmpty())throw new IllegalStateException("새 캡처의 정리가 끝난 뒤 지정하세요");
        JSONObject old=CaptureRepair.session(c);if(old.optJSONArray("shots")!=null&&old.optJSONArray("shots").length()>0)throw new IllegalStateException("기존 재캡처를 먼저 분석·적용하거나 취소·보관하세요");
        CaptureRepair.begin(c,folder,book);JSONObject s=CaptureRepair.session(c);s.put("startPage",value);CaptureRepair.saveSession(c,s);
        if(!CaptureRepair.prefs(c).edit().putLong(key(folder,book),value).commit())throw new java.io.IOException("시작 번호 저장 실패");
    }
    static void clear(Context c,String folder,String book)throws Exception{
        JSONObject s=CaptureRepair.session(c);if(s.optJSONArray("shots")!=null&&s.optJSONArray("shots").length()>0)throw new IllegalStateException("받은 재캡처를 먼저 분석·적용하거나 취소·보관하세요");CaptureRepair.cancel(c);CaptureRepair.prefs(c).edit().remove(key(folder,book)).commit();
    }
}
