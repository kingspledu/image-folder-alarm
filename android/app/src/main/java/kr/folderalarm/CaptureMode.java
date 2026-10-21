package kr.folderalarm;
import android.content.*;
import android.os.*;
import android.widget.Toast;
import org.json.*;
final class CaptureMode {
    static int step(Context c,String book){return c.getSharedPreferences("watch",0).getInt("captureStride:"+book,1)==2?2:1;}
    static void change(Context c,String book,int value)throws Exception{
        PageStride.check(value);if(!NamePolicy.validBook(book))throw new IllegalArgumentException("책 이름을 먼저 입력하세요");
        WatchService w=WatchService.live;if(w!=null&&book.equals(w.bookName)&&!w.pending.isEmpty())throw new IllegalStateException("캡처 정리가 끝난 뒤 모드를 바꾸세요");
        SharedPreferences.Editor edit=c.getSharedPreferences("watch",0).edit();
        JSONObject s=CaptureRepair.session(c);
        if(book.equals(s.optString("book"))){if(s.optJSONArray("shots")!=null&&s.optJSONArray("shots").length()>0)throw new IllegalStateException("재캡처를 먼저 분석·적용하거나 취소·보관한 뒤 모드를 바꾸세요");s.put("step",value);edit.putString("repairSession",s.toString());}
        if(!edit.putInt("captureStride:"+book,value).commit())throw new java.io.IOException("모드 저장 실패");
    }
    static void request(Context c,String book,int value,Runnable done){RepairOverlay.IO.execute(()->{String result;try{synchronized(WatchService.FILE_LOCK){change(c,book,value);}result=value+"페이지씩 캡처: 번호와 증가·감소가 "+value+"씩 움직입니다";}catch(Exception e){result=e.getMessage();}final String message=result;new Handler(Looper.getMainLooper()).post(()->{Toast.makeText(c,message,Toast.LENGTH_LONG).show();done.run();});});}
}
