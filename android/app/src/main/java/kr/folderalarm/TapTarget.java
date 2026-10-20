package kr.folderalarm;
import android.content.*;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

final class TapTarget {
    static LinearLayout panel;
    static WindowManager windows;
    static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    static void show(Context c){
        if(panel!=null)return;
        if(!Settings.canDrawOverlays(c)){MillieClickService.message(c,"다른 앱 위에 표시를 허용하세요");return;}
        if(!MillieClickService.millieVisible()){MillieClickService.message(c,"밀리 캡처 접근성을 켜고 밀리 화면에서 위치를 등록하세요");return;}
        windows=c.getSystemService(WindowManager.class);panel=new LinearLayout(c);panel.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xee103f48);bg.setCornerRadius(dp(c,8));panel.setBackground(bg);
        TextView target=new TextView(c);target.setText("⊕");target.setTextColor(0xffffdc43);target.setTextSize(32);target.setGravity(Gravity.CENTER);target.setContentDescription("조준점. 드래그하여 캡처 버튼 위에 놓으세요");panel.addView(target,new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));
        Button save=new Button(c);save.setText("저장");panel.addView(save);
        Button cancel=new Button(c);cancel.setText("취소");panel.addView(cancel);
        panel.removeView(target);panel.addView(target);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=dp(c,20);p.y=dp(c,140);
        target.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;
            public boolean onTouch(View v,android.view.MotionEvent e){
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();ox=p.x;oy=p.y;return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
                    android.util.DisplayMetrics m=MillieClickService.screen(c);p.x=Math.max(0,Math.min(m.widthPixels-panel.getWidth(),ox+Math.round(e.getRawX()-x)));p.y=Math.max(0,Math.min(m.heightPixels-dp(c,48),oy+Math.round(e.getRawY()-y)));
                    try{windows.updateViewLayout(panel,p);}catch(RuntimeException ex){close();}return true;
                }return true;
            }
        });
        save.setOnClickListener(v->{
            if(!MillieClickService.millieVisible()){MillieClickService.message(c,"밀리 화면이 아닙니다. 취소하고 다시 등록하세요");return;}
            int[] xy=new int[2];target.getLocationOnScreen(xy);android.util.DisplayMetrics m=MillieClickService.screen(c);
            int x=xy[0]+target.getWidth()/2,y=xy[1]+target.getHeight()/2;
            c.getSharedPreferences("watch",0).edit().putInt("tapX",x).putInt("tapY",y).putInt("tapW",m.widthPixels).putInt("tapH",m.heightPixels).apply();close();MillieClickService.message(c,"캡처 위치 저장: X "+x+", Y "+y);
        });cancel.setOnClickListener(v->close());
        try{windows.addView(panel,p);}catch(RuntimeException e){panel=null;MillieClickService.message(c,"위치 등록창을 표시하지 못했습니다");}
    }
    static void close(){if(panel!=null){try{windows.removeView(panel);}catch(RuntimeException ignored){}panel=null;}}
}
