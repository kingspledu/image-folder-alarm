package kr.folderalarm;
import android.accessibilityservice.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.Toast;

public class MillieClickService extends AccessibilityService {
    static final String TARGET="kr.co.millie.millieshelf";
    static MillieClickService instance;
    final Handler main=new Handler(Looper.getMainLooper());
    boolean busy;
    protected void onServiceConnected(){instance=this;}
    public void onAccessibilityEvent(AccessibilityEvent event){}
    public void onInterrupt(){busy=false;}
    public void onDestroy(){if(instance==this)instance=null;busy=false;super.onDestroy();}
    static boolean ready(){return instance!=null;}
    static boolean millieVisible(){
        MillieClickService s=instance;if(s==null)return false;
        AccessibilityNodeInfo root=s.getRootInActiveWindow();
        return root!=null&&TARGET.contentEquals(root.getPackageName()==null?"":root.getPackageName());
    }
    static android.util.DisplayMetrics screen(Context c){
        android.util.DisplayMetrics m=new android.util.DisplayMetrics();c.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(m);return m;
    }
    static void press(Context c,Runnable finished){
        MillieClickService s=instance;
        if(s==null){message(c,"앱에서 밀리 캡처 접근성을 먼저 켜세요");finished.run();return;}
        if(s.busy){finished.run();return;}
        if(!millieVisible()){message(c,"밀리의 서재 화면에서만 누를 수 있습니다");finished.run();return;}
        SharedPreferences p=c.getSharedPreferences("watch",0);android.util.DisplayMetrics m=screen(c);
        int x=p.getInt("tapX",-1),y=p.getInt("tapY",-1);
        if(x<0||y<0||x>=m.widthPixels||y>=m.heightPixels||p.getInt("tapW",0)!=m.widthPixels||p.getInt("tapH",0)!=m.heightPixels){
            message(c,"현재 화면 방향과 해상도에서 캡처 위치를 다시 등록하세요");finished.run();return;
        }
        s.busy=true;
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,40)).build();
        try{
            boolean accepted=s.dispatchGesture(gesture,new GestureResultCallback(){
                public void onCompleted(GestureDescription g){s.busy=false;finished.run();}
                public void onCancelled(GestureDescription g){s.busy=false;message(c,"터치가 취소되었습니다. 다시 눌러주세요");finished.run();}
            },s.main);
            if(!accepted){s.busy=false;message(c,"터치를 실행하지 못했습니다");finished.run();}
        }catch(RuntimeException e){s.busy=false;message(c,"접근성 터치를 실행하지 못했습니다");finished.run();}
    }
    static void message(Context c,String s){Toast.makeText(c,s,Toast.LENGTH_LONG).show();}
}
