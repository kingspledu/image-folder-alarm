package kr.folderalarm;
import java.io.InterruptedIOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
final class AnalysisProgress {
 interface Listener {void update(int done,int total,String file,int matches);}
 final AtomicBoolean cancelled=new AtomicBoolean();final Listener listener;
 final Set<String> seen=new HashSet<>();int done,total,matches;
 AnalysisProgress(int total,Listener listener){this.total=total;this.listener=listener;}
 void check()throws InterruptedIOException {if(cancelled.get())throw new InterruptedIOException("검사를 취소했습니다. 이미지 파일은 변경하지 않았습니다.");}
 void before(String file)throws InterruptedIOException {check();listener.update(done,total,file,matches);}
 void after(String hash,String file)throws InterruptedIOException {check();if(!seen.add(hash))matches++;done++;listener.update(done,total,file,matches);}
}
