package kr.folderalarm;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

final class FloatingCounter {
    final Context context;
    final Handler main=new Handler(Looper.getMainLooper());
    final WindowManager windows;
    LinearLayout root;
    TextView label,number;
    WindowManager.LayoutParams params;
    boolean destroyed;
    volatile boolean visible;
    FloatingCounter(Context c){context=c;windows=c.getSystemService(WindowManager.class);}
    int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    void show(String book){
        if(destroyed||root!=null||!Settings.canDrawOverlays(context))return;
        root=new LinearLayout(context);root.setOrientation(LinearLayout.HORIZONTAL);root.setGravity(Gravity.CENTER_VERTICAL);root.setPadding(dp(12),dp(7),dp(4),dp(7));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(28,49,39));bg.setCornerRadius(dp(14));root.setBackground(bg);root.setElevation(dp(6));
        LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
        label=new TextView(context);label.setText(book);label.setTextColor(0xffc4d8c8);label.setTextSize(10);label.setMaxWidth(dp(130));label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        number=new TextView(context);number.setText("준비 중");number.setTextColor(Color.WHITE);number.setTextSize(22);number.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        content.addView(label);content.addView(number);root.addView(content);
        Button capture=new Button(context);capture.setText("캡처");capture.setContentDescription("등록된 밀리 캡처 버튼 한 번 누르기");
        root.addView(capture,new LinearLayout.LayoutParams(dp(72),dp(48)));
        capture.setOnClickListener(v->{
            if(!MillieClickService.ready()){MillieClickService.message(context,"앱에서 밀리 캡처 접근성을 먼저 켜세요");return;}
            hide();
            main.postDelayed(()->{
                if(destroyed)return;
                MillieClickService.press(context,()->main.postDelayed(()->{
                    if(!destroyed&&context.getSharedPreferences("watch",0).getBoolean("floating",true)){
                        show(book);update(WatchService.displayNumber(),WatchService.displayPending());
                    }
                },160));
            },80);
        });
        Button position=new Button(context);position.setText("위치");position.setContentDescription("캡처 터치 위치 등록");root.addView(position,new LinearLayout.LayoutParams(dp(64),dp(48)));
        position.setOnClickListener(v->TapTarget.show(context));
        TextView close=new TextView(context);close.setText("×");close.setTextColor(0xffc4d8c8);close.setTextSize(23);close.setGravity(Gravity.CENTER);close.setContentDescription("번호창 닫기. 감시는 계속됩니다");
        root.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
        close.setOnClickListener(v->{context.getSharedPreferences("watch",0).edit().putBoolean("floating",false).apply();hide();});
        params=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;
        android.content.SharedPreferences p=context.getSharedPreferences("watch",0);
        params.x=p.getInt("floatX",dp(12));params.y=p.getInt("floatY",dp(100));
        content.setContentDescription("드래그해서 번호창 이동");
        content.setOnTouchListener(new View.OnTouchListener(){
            float startX,startY;int originX,originY;boolean moved;
            public boolean onTouch(View v,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:startX=e.getRawX();startY=e.getRawY();originX=params.x;originY=params.y;moved=false;return true;
                    case MotionEvent.ACTION_MOVE:
                        if(root==null)return true;
                        int dx=Math.round(e.getRawX()-startX),dy=Math.round(e.getRawY()-startY);moved|=Math.abs(dx)+Math.abs(dy)>dp(4);
                        android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
                        params.x=Math.max(0,Math.min(metrics.widthPixels-root.getWidth(),originX+dx));
                        params.y=Math.max(0,Math.min(metrics.heightPixels-root.getHeight()-dp(30),originY+dy));
                        try{windows.updateViewLayout(root,params);}catch(RuntimeException ignored){hide();}return true;
                    case MotionEvent.ACTION_UP:
                        context.getSharedPreferences("watch",0).edit().putInt("floatX",params.x).putInt("floatY",params.y).apply();
                        if(!moved)v.performClick();return true;
                    case MotionEvent.ACTION_CANCEL:return true;
                }return false;
            }
        });
        try{windows.addView(root,params);visible=true;}catch(RuntimeException e){root=null;Toast.makeText(context,"번호창을 표시하지 못했습니다. 다른 앱 위에 표시 권한을 확인하세요",Toast.LENGTH_LONG).show();}
    }
    void update(long last,int pending){main.post(()->{
        if(destroyed||root==null)return;
        number.setText(String.format(java.util.Locale.ROOT,"%03d",last)+(pending>0?" · 정리 중":""));
    });}
    void hide(){visible=false;if(root!=null){try{windows.removeView(root);}catch(RuntimeException ignored){}root=null;}}
    void destroy(){TapTarget.close();destroyed=true;main.removeCallbacksAndMessages(null);hide();}
}
