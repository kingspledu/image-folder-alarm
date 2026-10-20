package kr.folderalarm;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

final class RepairOverlay {
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    final Context context;final WindowManager windows;final Handler main=new Handler(Looper.getMainLooper());
    LinearLayout panel;TextView status,caption;Button apply;ImageView image;Bitmap bitmap;
    String folder,book;RepairPlan plan;boolean busy;int generation,index;
    RepairOverlay(Context c){context=c;windows=c.getSystemService(WindowManager.class);}
    int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    Button button(LinearLayout row,String title,Runnable action){Button b=new Button(context);b.setText(title);b.setTextSize(11);row.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));b.setOnClickListener(v->{if(!busy)action.run();});return b;}
    LinearLayout row(){LinearLayout row=new LinearLayout(context);panel.addView(row);return row;}
    void show(String selectedBook){
        if(panel!=null)return;if(busy){toast("보정 처리 중입니다. 잠시 기다려 주세요");return;}
        if(!Settings.canDrawOverlays(context))return;
        book=selectedBook;folder=CaptureRepair.prefs(context).getString("uri","");if(folder.isEmpty())return;plan=null;generation++;
        panel=new LinearLayout(context);panel.setOrientation(1);panel.setPadding(dp(12),dp(8),dp(12),dp(8));panel.setBackgroundColor(0xfff2f5ee);
        TextView title=new TextView(context);title.setText("중복 검사 / 겹치는 구간 재캡처");title.setTextColor(0xff20392e);title.setTextSize(16);panel.addView(title);
        status=new TextView(context);status.setTextColor(0xff20392e);status.setTextSize(12);status.setText("재캡처 시작 → 빠진 구간 앞뒤의 기존 페이지까지 순서대로 겹쳐 캡처 → 종료·분석 → 제안 확인 후 적용\n상주창의 시작 페이지를 지정하면 실제 페이지 기준과 중복을 함께 분석합니다. 페이지 숫자를 읽는 기능은 아닙니다. 같은 화면 크기로 캡처하세요.\n"+CaptureRepair.label(context));
        ScrollView scroll=new ScrollView(context);scroll.addView(status);panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(130)));
        LinearLayout first=row();button(first,"재캡처 시작/추가",()->work(0));button(first,"종료·분석",()->work(1));
        LinearLayout second=row();button(second,"기존 중복 검사",()->work(2));button(second,"취소·파일보관",()->work(3));
        image=new ImageView(context);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("적용될 이미지 순서 미리보기");panel.addView(image,new LinearLayout.LayoutParams(-1,dp(110)));
        caption=new TextView(context);caption.setTextColor(0xff20392e);caption.setTextSize(11);panel.addView(caption);
        LinearLayout navigation=row();button(navigation,"이전",()->{if(plan!=null&&index>0){index--;thumbnail();}});button(navigation,"다음",()->{if(plan!=null&&index+1<plan.after.size()){index++;thumbnail();}});
        LinearLayout controls=row();apply=button(controls,"제안 적용(원본보관)",()->confirm());apply.setEnabled(false);button(controls,"중단 복구",()->work(5));button(controls,"닫기",()->close());
        android.util.DisplayMetrics m=context.getResources().getDisplayMetrics();WindowManager.LayoutParams params=new WindowManager.LayoutParams(Math.min(dp(355),m.widthPixels-dp(16)),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);params.gravity=Gravity.CENTER;
        try{windows.addView(panel,params);}catch(RuntimeException e){panel=null;toast("보정 창을 열지 못했습니다");}
    }
    interface Task{String run()throws Exception;}
    void confirm(){if(plan==null)return;status.setText(plan.summary+"\n\n적용 중에는 캡처를 잠시 멈추세요. 중복·교체된 원본은 __backup_ 파일로 남습니다. 아래 버튼을 한 번 더 누르면 적용합니다.");apply.setText("확인: 보관 후 적용");apply.setOnClickListener(v->{if(!busy)work(4);});}
    void work(int action){
        if(busy)return;busy=true;apply.setEnabled(false);status.setText("처리 중… 이미지 수에 따라 시간이 걸릴 수 있습니다. 적용 중에는 캡처를 멈춰 주세요.");final int token=generation;
        IO.execute(()->{
            String message;
            try{synchronized(WatchService.FILE_LOCK){
                WatchService watch=WatchService.live;
                if(action!=3&&action!=5&&watch!=null&&!watch.pending.isEmpty())throw new IllegalStateException("새 이미지 정리 중입니다. 잠시 기다린 뒤 다시 누르세요");
                if(action==0){if(watch==null||watch.known==null)throw new IllegalStateException("감시를 시작하고 준비가 끝난 뒤 다시 누르세요");CaptureRepair.begin(context,folder,book);plan=null;message="재캡처 시작. 기존 앞쪽 페이지부터 빠진 구간을 지나 기존 뒤쪽 페이지까지 순서대로 캡처하세요.";}
                else if(action==1){plan=CaptureRepair.finish(context,folder,book);message=plan.summary;}
                else if(action==2){if(!CaptureRepair.session(context).optString("folder").isEmpty())throw new IllegalStateException("재캡처 묶음을 적용하거나 취소·보관한 뒤 기존 중복 검사하세요");plan=RepairPlan.dedupe(CaptureRepair.bookPages(context,folder,book),book);message=plan.summary;}
                else if(action==3){CaptureRepair.cancel(context);plan=null;message="재캡처를 취소했습니다. 이미 받은 __recap_ 파일은 삭제하지 않고 남겼습니다.";}
                else if(action==4){if(plan==null)throw new IllegalStateException("먼저 분석하세요");CaptureRepair.apply(context,folder,book,plan);plan=null;message="보정 완료. 중복·교체 원본은 __backup_ 파일로 보관했습니다. 다음 캡처를 진행하세요.";}
                else {CaptureRepair.recover(context,folder);plan=null;message="중단된 작업의 파일명을 복구했습니다.";}
            }}catch(Exception e){plan=null;message=e.getMessage()==null?"처리하지 못했습니다. 원본 파일을 확인하세요":e.getMessage();}
            final String result=message;main.post(()->{busy=false;if(panel==null||token!=generation)return;status.setText(result);apply.setText("제안 적용(원본보관)");apply.setOnClickListener(v->{if(!busy)confirm();});apply.setEnabled(plan!=null);index=0;if(plan!=null){while(index+1<plan.after.size()&&index<plan.before.size()&&plan.after.get(index).uri.equals(plan.before.get(index).uri))index++;thumbnail();}else clearImage();});
        });
    }
    void thumbnail(){
        if(plan==null||plan.after.isEmpty())return;final RepairPlan.Page page=plan.after.get(index);final int token=generation;final int which=index;caption.setText("적용 후 "+plan.numbers.get(index)+"페이지 · 총 "+plan.after.size()+"장"+" · "+(page.name.startsWith("__recap_")?"재캡처 이미지":page.name));
        IO.execute(()->{Bitmap decoded=null;try{BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;try(InputStream in=context.getContentResolver().openInputStream(android.net.Uri.parse(page.uri))){BitmapFactory.decodeStream(in,null,b);}b.inJustDecodeBounds=false;b.inSampleSize=1;while(b.outWidth/b.inSampleSize>900||b.outHeight/b.inSampleSize>900)b.inSampleSize*=2;try(InputStream in=context.getContentResolver().openInputStream(android.net.Uri.parse(page.uri))){decoded=BitmapFactory.decodeStream(in,null,b);}}catch(Exception ignored){}final Bitmap picture=decoded;main.post(()->{if(panel==null||token!=generation||which!=index){if(picture!=null)picture.recycle();return;}clearImage();bitmap=picture;image.setImageBitmap(picture);});});
    }
    void clearImage(){if(image!=null)image.setImageDrawable(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    void close(){generation++;if(panel!=null){try{windows.removeView(panel);}catch(RuntimeException ignored){}panel=null;}clearImage();}
    void toast(String s){Toast.makeText(context,s,Toast.LENGTH_LONG).show();}
}
