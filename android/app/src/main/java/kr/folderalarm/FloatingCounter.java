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
    Button minusButton,plusButton,modeButton;
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
        LinearLayout tools=new LinearLayout(context);
        Button latest=new Button(context);latest.setText("마지막 이미지 / 삭제");latest.setTextSize(12);tools.addView(latest,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(tools);
        latest.setOnClickListener(v->preview.show(book));
        Button repairButton=new Button(context);repairButton.setText("중복 / 구간 보정");repairButton.setTextSize(11);tools.addView(repairButton,new LinearLayout.LayoutParams(0,dp(44),1));repairButton.setOnClickListener(v->repair.show(book));
        LinearLayout adjust=new LinearLayout(context);root.addView(adjust);
        minusButton=adjustmentButton(adjust,"−1",()->shiftOffset(-1));
        adjustmentButton(adjust,"시작 페이지",()->editOffset());
        plusButton=adjustmentButton(adjust,"+1",()->shiftOffset(1));
        modeButton=adjustmentButton(adjust,"1쪽씩",()->CaptureMode.request(context,activeBook,CaptureMode.step(context,activeBook)==1?2:1,()->{if(!destroyed)renderNumber();}));
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
    void update(long last,int pending){final long page=PageStart.shown(context,pageFolder(),activeBook,last,pending);main.post(()->{
        if(destroyed||root==null)return;
        preview.changed(last,pending);
        actualNumber=last;pendingNow=pending;renderNumber(page);
    });}
    String pageFolder(){return context.getSharedPreferences("watch",0).getString("uri","");}
    long shownPage(){return PageStart.shown(context,pageFolder(),activeBook,actualNumber,pendingNow);}
    void renderNumber(){renderNumber(shownPage());}
    void renderNumber(long page){
        if(root==null)return;
        number.setText("다음 "+String.format(java.util.Locale.ROOT,"%03d",page)+(pendingNow>0?" · 정리 중":""));
        number.setMaxWidth(dp(145));
        int stride=CaptureMode.step(context,activeBook);
        if(minusButton!=null)minusButton.setText("−"+stride);if(plusButton!=null)plusButton.setText("+"+stride);if(modeButton!=null)modeButton.setText(stride+"쪽씩");
        correction.setText(stride+"쪽씩 · 저장 끝 번호 "+actualNumber+CaptureRepair.label(context));
    }
    void changePage(long value,boolean reset){
        final String folder=pageFolder(),book=activeBook;
        RepairOverlay.IO.execute(()->{
            String message;
            try{synchronized(WatchService.FILE_LOCK){if(reset)PageStart.clear(context,folder,book);else PageStart.select(context,folder,book,value);}message=reset?"다음 캡처는 저장 끝 번호 뒤로 이어갑니다":"다음 첫 캡처의 시작 페이지: "+value+". 촬영 후 중복 / 구간 보정에서 종료·분석하세요";}
            catch(Exception e){message=e.getMessage()==null?"페이지를 지정하지 못했습니다":e.getMessage();}
            final String text=message;main.post(()->{if(!destroyed)renderNumber();Toast.makeText(context,text,Toast.LENGTH_LONG).show();});
        });
    }
    void shiftOffset(int direction){long next=shownPage()+(long)direction*CaptureMode.step(context,activeBook);if(next>=1&&next<=1000000000L)changePage(next,false);}
    Button adjustmentButton(LinearLayout row,String text,Runnable action){Button b=new Button(context);b.setText(text);b.setTextSize(12);row.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));b.setOnClickListener(v->action.run());return b;}
    void editOffset(){
        if(adjustmentPanel!=null)return;
        adjustmentPanel=new LinearLayout(context);adjustmentPanel.setOrientation(1);adjustmentPanel.setPadding(dp(16),dp(12),dp(16),dp(12));adjustmentPanel.setBackgroundColor(0xfff3f6ef);
        TextView help=new TextView(context);help.setText("다음 첫 캡처의 실제 페이지\n예: 016 지정 → 다음 이미지는 16페이지 기준.\n앞뒤 같은 페이지까지 순서대로 겹쳐 찍고\n중복 / 구간 보정 → 종료·분석에서 적용하세요.\n교체 원본은 백업하며 충돌 시 적용하지 않습니다.");help.setTextColor(0xff20392e);help.setTextSize(13);adjustmentPanel.addView(help);
        EditText input=new EditText(context);input.setSingleLine(true);input.setTextColor(0xff20392e);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setText(String.valueOf(shownPage()));input.selectAll();adjustmentPanel.addView(input);
        TextView error=new TextView(context);error.setTextColor(0xffa03025);error.setTextSize(12);adjustmentPanel.addView(error);
        LinearLayout actions=new LinearLayout(context);adjustmentPanel.addView(actions);
        adjustmentButton(actions,"시작 지정",()->{try{long value=Long.parseLong(input.getText().toString().trim());if(value<1||value>1000000000L)throw new NumberFormatException();changePage(value,false);closeAdjustment();}catch(NumberFormatException e){error.setText("1~1000000000 사이의 페이지를 입력하세요");}});
        adjustmentButton(actions,"끝에 이어찍기",()->{changePage(0,true);closeAdjustment();});adjustmentButton(actions,"취소",()->closeAdjustment());
        android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(dp(330),metrics.widthPixels-dp(24)),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.CENTER;p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try{windows.addView(adjustmentPanel,p);input.requestFocus();main.postDelayed(()->{if(adjustmentPanel!=null)context.getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);},120);}catch(RuntimeException e){adjustmentPanel=null;Toast.makeText(context,"페이지 입력창을 열지 못했습니다",Toast.LENGTH_LONG).show();}
    }
    void closeAdjustment(){if(adjustmentPanel!=null){context.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(adjustmentPanel.getWindowToken(),0);try{windows.removeView(adjustmentPanel);}catch(RuntimeException ignored){}adjustmentPanel=null;}}
    void hide(){closeAdjustment();visible=false;if(root!=null){try{windows.removeView(root);}catch(RuntimeException ignored){}root=null;}}
    void destroy(){repair.close();preview.close();destroyed=true;main.removeCallbacksAndMessages(null);hide();}
}
