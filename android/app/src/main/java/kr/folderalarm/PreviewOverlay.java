package kr.folderalarm;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.util.concurrent.*;

final class PreviewOverlay {
    static final ExecutorService IO=Executors.newSingleThreadExecutor();
    final Context context;final WindowManager windows;final Handler main=new Handler(Looper.getMainLooper());
    LinearLayout panel,confirmation;TextView filename,question;ImageView image;Button delete,refresh;
    DeleteLast.Entry selected;Bitmap bitmap;String folder,book;int generation;boolean deleting,loading;long shownNumber=-1;
    PreviewOverlay(Context c){context=c;windows=c.getSystemService(WindowManager.class);}
    int dp(int x){return Math.round(x*context.getResources().getDisplayMetrics().density);}
    Button button(LinearLayout parent,String name,Runnable action){Button b=new Button(context);b.setText(name);b.setTextSize(12);b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));return b;}
    void show(String title){
        if(deleting){toast("삭제 처리 중입니다. 잠시 후 다시 열어주세요");return;}
        if(panel!=null){if(!deleting)load();return;}
        if(!Settings.canDrawOverlays(context)){toast("미리보기에 다른 앱 위에 표시 권한이 필요합니다");return;}
        book=title;folder=context.getSharedPreferences("watch",0).getString("uri","");
        if(folder.isEmpty()){toast("폴더를 먼저 선택하세요");return;}
        panel=new LinearLayout(context);panel.setOrientation(1);panel.setPadding(dp(12),dp(8),dp(12),dp(8));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff20392e);bg.setCornerRadius(dp(14));panel.setBackground(bg);
        filename=new TextView(context);filename.setTextColor(Color.WHITE);filename.setTextSize(14);filename.setMaxLines(2);filename.setPadding(0,0,0,dp(6));panel.addView(filename);
        image=new ImageView(context);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("마지막 번호 이미지 미리보기");panel.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(context);refresh=button(actions,"새로고침",()->load());delete=button(actions,"한 장 삭제",()->confirm());button(actions,"닫기",()->close());panel.addView(actions);
        confirmation=new LinearLayout(context);confirmation.setOrientation(1);question=new TextView(context);question.setTextColor(0xffffd697);question.setTextSize(12);confirmation.addView(question);
        LinearLayout confirmButtons=new LinearLayout(context);button(confirmButtons,"영구 삭제",()->remove());button(confirmButtons,"취소",()->confirmation.setVisibility(View.GONE));confirmation.addView(confirmButtons);confirmation.setVisibility(View.GONE);panel.addView(confirmation);
        android.util.DisplayMetrics m=context.getResources().getDisplayMetrics();
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(Math.min(dp(340),m.widthPixels-dp(24)),Math.min(dp(500),m.heightPixels-dp(100)),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);params.gravity=Gravity.CENTER;
        try{windows.addView(panel,params);load();}catch(RuntimeException e){panel=null;toast("미리보기 창을 표시하지 못했습니다");}
    }
    void changed(long last,int pending){if(panel!=null&&!loading&&!deleting&&pending==0&&last!=shownNumber&&confirmation.getVisibility()!=View.VISIBLE)load();}
    void clearBitmap(){if(image!=null)image.setImageDrawable(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    void load(){
        if(panel==null||deleting||loading)return;
        loading=true;final int token=++generation;delete.setEnabled(false);refresh.setEnabled(false);confirmation.setVisibility(View.GONE);filename.setText("마지막 이미지 불러오는 중…");
        IO.execute(()->{
            DeleteLast.Entry entry=null;Bitmap decoded=null;String error=null;
            try{
                synchronized(WatchService.FILE_LOCK){entry=DeleteLast.find(context,folder,book);}
                if(entry!=null){
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                    try(InputStream input=context.getContentResolver().openInputStream(entry.uri)){if(input==null)throw new IllegalStateException();BitmapFactory.decodeStream(input,null,bounds);}
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                    while(bounds.outWidth/options.inSampleSize>1200||bounds.outHeight/options.inSampleSize>1200)options.inSampleSize*=2;
                    try(InputStream input=context.getContentResolver().openInputStream(entry.uri)){if(input==null)throw new IllegalStateException();decoded=BitmapFactory.decodeStream(input,null,options);}
                    if(decoded==null)error="이 형식은 미리보기를 열지 못했습니다";
                }
            }catch(Exception e){error="이미지를 읽지 못했습니다. 폴더 권한을 확인하세요";}
            final DeleteLast.Entry found=entry;final Bitmap picture=decoded;final String problem=error;
            main.post(()->{
                if(token!=generation||panel==null){if(picture!=null)picture.recycle();return;}
                loading=false;selected=found;clearBitmap();bitmap=picture;image.setImageBitmap(picture);
                shownNumber=found==null?0:found.number;
                filename.setText(found==null?"이 책 이름으로 저장된 이미지가 없습니다":found.name+(problem==null?"":"\n"+problem));
                delete.setEnabled(found!=null&&(found.flags&DocumentsContract.Document.FLAG_SUPPORTS_DELETE)!=0);refresh.setEnabled(true);
                if(found==null&&problem!=null)filename.setText(problem);
            });
        });
    }
    void confirm(){if(panel==null||selected==null||loading||deleting)return;question.setText(selected.name+"\n이 파일 한 장을 영구 삭제할까요? 휴지통으로 이동하지 않습니다.");confirmation.setVisibility(View.VISIBLE);}
    void remove(){
        if(panel==null||selected==null||loading||deleting)return;
        deleting=true;delete.setEnabled(false);refresh.setEnabled(false);final DeleteLast.Entry expected=selected;final int token=generation;
        IO.execute(()->{
            String result;
            try{synchronized(WatchService.FILE_LOCK){
                DeleteLast.Entry latest=DeleteLast.find(context,folder,book);
                if(latest==null||!latest.uri.equals(expected.uri)||!latest.name.equals(expected.name))result="마지막 파일이 바뀌어 삭제하지 않았습니다. 미리보기를 다시 확인하세요";
                else if(DocumentsContract.deleteDocument(context.getContentResolver(),expected.uri))result="삭제 완료: "+expected.name;
                else result="삭제하지 못했습니다";
            }}catch(Exception e){result="삭제하지 못했습니다. 파일과 폴더 권한을 확인하세요";}
            final String message=result;main.post(()->{deleting=false;toast(message);if(WatchService.live!=null)WatchService.live.requestScan();if(panel!=null&&token==generation){confirmation.setVisibility(View.GONE);load();}});
        });
    }
    void toast(String message){Toast.makeText(context,message,Toast.LENGTH_LONG).show();}
    void close(){generation++;loading=false;if(panel!=null){try{windows.removeView(panel);}catch(RuntimeException ignored){}panel=null;}selected=null;shownNumber=-1;clearBitmap();image=null;}
}
