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
    final PreviewOverlay preview;
    final RepairOverlay repair;
    LinearLayout root;
    TextView label,number,correction;
    String activeBook;
    long actualNumber;
    int pendingNow;
    LinearLayout adjustmentPanel;
    WindowManager.LayoutParams params;
    boolean destroyed;
    volatile boolean visible;
    FloatingCounter(Context c){context=c;windows=c.getSystemService(WindowManager.class);preview=new PreviewOverlay(c);repair=new RepairOverlay(c);}
    int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    void show(String book){
        if(destroyed||root!=null||!Settings.canDrawOverlays(context))return;
        activeBook=book;
        root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_VERTICAL);root.setPadding(dp(12),dp(7),dp(4),dp(7));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(28,49,39));bg.setCornerRadius(dp(14));root.setBackground(bg);root.setElevation(dp(6));
        LinearLayout controls=new LinearLayout(context);controls.setGravity(Gravity.CENTER_VERTICAL);root.addView(controls);
        LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
        label=new TextView(context);label.setText(book);label.setTextColor(0xffc4d8c8);label.setTextSize(10);label.setMaxWidth(dp(130));label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        number=new TextView(context);number.setText("준비 중");number.setTextColor(Color.WHITE);number.setTextSize(22);number.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        correction=new TextView(context);correction.setTextColor(0xffc4d8c8);correction.setTextSize(10);
        content.addView(label);content.addView(number);content.addView(correction);controls.addView(content);
        Button capture=new Button(context);capture.setText("캡처");capture.setContentDescription("등록된 밀리 캡처 버튼 한 번 누르기");
        controls.addView(capture,new LinearLayout.LayoutParams(dp(72),dp(48)));
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
        Button position=new Button(context);position.setText("위치");position.setContentDescription("캡처 터치 위치 등록");controls.addView(position,new LinearLayout.LayoutParams(dp(64),dp(48)));
        position.setOnClickListener(v->TapTarget.show(context));
        LinearLayout tools=new LinearLayout(context);
        Button latest=new Button(context);latest.setText("마지막 이미지 / 삭제");latest.setTextSize(12);tools.addView(latest,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(tools);
        latest.setOnClickListener(v->preview.show(book));
        Button repairButton=new Button(context);repairButton.setText("중복 / 구간 보정");repairButton.setTextSize(11);tools.addView(repairButton,new LinearLayout.LayoutParams(0,dp(44),1));repairButton.setOnClickListener(v->repair.show(book));
        LinearLayout adjust=new LinearLayout(context);root.addView(adjust);
        adjustmentButton(adjust,"−1",()->shiftOffset(-1));
        adjustmentButton(adjust,"표시 보정",()->editOffset());
        adjustmentButton(adjust,"+1",()->shiftOffset(1));
        TextView close=new TextView(context);close.setText("×");close.setTextColor(0xffc4d8c8);close.setTextSize(23);close.setGravity(Gravity.CENTER);close.setContentDescription("번호창 닫기. 감시는 계속됩니다");
        controls.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
        close.setOnClickListener(v->{repair.close();preview.close();context.getSharedPreferences("watch",0).edit().putBoolean("floating",false).apply();hide();});
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
        try{windows.addView(root,params);visible=true;renderNumber();}catch(RuntimeException e){root=null;Toast.makeText(context,"번호창을 표시하지 못했습니다. 다른 앱 위에 표시 권한을 확인하세요",Toast.LENGTH_LONG).show();}
    }
    void update(long last,int pending){main.post(()->{
        if(destroyed||root==null)return;
        preview.changed(last,pending);
        actualNumber=last;pendingNow=pending;renderNumber();
    });}
    int offset(){return context.getSharedPreferences("watch",0).getInt("displayOffset:"+activeBook,0);}
    void renderNumber(){
        if(root==null)return;
        int delta=offset();
        number.setText(String.format(java.util.Locale.ROOT,"%03d",DisplayNumber.value(actualNumber,delta))+(pendingNow>0?" · 정리 중":""));
        correction.setText("실제 "+actualNumber+" · 보정 "+(delta>=0?"+":"")+delta+CaptureRepair.label(context));
    }
    void saveOffset(int value){context.getSharedPreferences("watch",0).edit().putInt("displayOffset:"+activeBook,value).apply();renderNumber();}
    void shiftOffset(int direction){
        int current=offset();if(direction>0&&current==Integer.MAX_VALUE||direction<0&&current==Integer.MIN_VALUE)return;saveOffset(current+direction);
    }
    void adjustmentButton(LinearLayout row,String text,Runnable action){Button b=new Button(context);b.setText(text);b.setTextSize(12);row.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));b.setOnClickListener(v->action.run());}
    void editOffset(){
        if(adjustmentPanel!=null)return;
        adjustmentPanel=new LinearLayout(context);adjustmentPanel.setOrientation(1);adjustmentPanel.setPadding(dp(16),dp(12),dp(16),dp(12));adjustmentPanel.setBackgroundColor(0xfff3f6ef);
        TextView help=new TextView(context);help.setText("표시 번호 보정\n음수는 줄이고 양수는 늘립니다.\n예: 실제 50 + 보정 −7 → 표시 43\n파일명과 이미지는 바뀌지 않습니다.");help.setTextColor(0xff20392e);help.setTextSize(14);adjustmentPanel.addView(help);
        EditText input=new EditText(context);input.setSingleLine(true);input.setTextColor(0xff20392e);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);input.setText(String.valueOf(offset()));input.selectAll();adjustmentPanel.addView(input);
        TextView error=new TextView(context);error.setTextColor(0xffa03025);error.setTextSize(12);adjustmentPanel.addView(error);
        LinearLayout actions=new LinearLayout(context);adjustmentPanel.addView(actions);
        adjustmentButton(actions,"적용",()->{try{saveOffset(Integer.parseInt(input.getText().toString().trim()));closeAdjustment();}catch(NumberFormatException e){error.setText("−2147483648부터 2147483647까지 정수를 입력하세요");}});
        adjustmentButton(actions,"초기화",()->{saveOffset(0);closeAdjustment();});adjustmentButton(actions,"취소",()->closeAdjustment());
        android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(dp(320),metrics.widthPixels-dp(24)),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.CENTER;p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try{windows.addView(adjustmentPanel,p);input.requestFocus();main.postDelayed(()->{if(adjustmentPanel!=null)context.getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);},120);}catch(RuntimeException e){adjustmentPanel=null;Toast.makeText(context,"보정 입력창을 열지 못했습니다",Toast.LENGTH_LONG).show();}
    }
    void closeAdjustment(){if(adjustmentPanel!=null){context.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(adjustmentPanel.getWindowToken(),0);try{windows.removeView(adjustmentPanel);}catch(RuntimeException ignored){}adjustmentPanel=null;}}
    void hide(){closeAdjustment();visible=false;if(root!=null){try{windows.removeView(root);}catch(RuntimeException ignored){}root=null;}}
    void destroy(){repair.close();preview.close();TapTarget.close();destroyed=true;main.removeCallbacksAndMessages(null);hide();}
}
