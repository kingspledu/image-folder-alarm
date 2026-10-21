package kr.folderalarm;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExportActivity extends Activity {
    final ExecutorService io=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());final AtomicBoolean cancelled=new AtomicBoolean();
    TextView status;Button drive,share,save,create,cancel;String book,folder;ZipExport.Result result;boolean busy;
    public void onCreate(Bundle state){super.onCreate(state);book=getIntent().getStringExtra("book");if(book==null)book=CaptureRepair.prefs(this).getString("bookName","");folder=CaptureRepair.prefs(this).getString("uri","");
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(32,50,32,32);
        TextView title=new TextView(this);title.setText("책 캡처 ZIP / Google Drive");title.setTextSize(22);layout.addView(title);
        status=new TextView(this);status.setTextSize(15);status.setPadding(0,20,0,24);status.setText(book+"\n\n현재 책의 번호가 붙은 최종 이미지만 압축합니다. 백업·재캡처 대기 파일은 제외합니다. 원본은 유지합니다.\n\n압축 중에는 캡처를 잠시 멈추고 이 화면을 유지하세요. 검증이 끝나면 Google Drive에서 계정·폴더를 골라 저장할 수 있습니다.");layout.addView(status);
        create=button(layout,"ZIP 만들기",()->startExport());drive=button(layout,"Google Drive에 저장",()->send(true));share=button(layout,"다른 앱으로 공유",()->send(false));save=button(layout,"파일로 저장 / 저장 위치 선택",()->saveAs());
        drive.setEnabled(false);share.setEnabled(false);save.setEnabled(false);cancel=button(layout,"압축 취소",()->cancelled.set(true));cancel.setEnabled(false);button(layout,"돌아가기",()->onBackPressed());
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});setContentView(scroll);
        if(state!=null&&state.containsKey("token")){String token=state.getString("token");if(new File(ExportProvider.directory(this),token+".zip").isFile()){result=new ZipExport.Result(token,state.getString("name"),state.getLong("bytes"),state.getInt("count"));ready();}}
    }
    Button button(LinearLayout l,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());l.addView(b);return b;}
    void startExport(){if(busy)return;busy=true;cancelled.set(false);create.setEnabled(false);drive.setEnabled(false);share.setEnabled(false);save.setEnabled(false);cancel.setEnabled(true);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final android.content.Context app=getApplicationContext();final String selectedBook=book,selectedFolder=folder;
        io.execute(()->{try{ZipExport.Result ready=ZipExport.create(app,selectedFolder,selectedBook,cancelled,text->main.post(()->{if(!isFinishing())status.setText(text);}));main.post(()->{result=ready;finished();ready();});}catch(Exception e){main.post(()->{finished();status.setText(e.getMessage()==null?"ZIP을 만들지 못했습니다. 저장 공간과 폴더 권한을 확인하세요":e.getMessage());});}});
    }
    void finished(){busy=false;create.setEnabled(true);cancel.setEnabled(false);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(result!=null){drive.setEnabled(true);share.setEnabled(true);save.setEnabled(true);}}
    void ready(){finished();status.setText("검증 완료: "+result.name+"\n이미지 "+result.count+"장 · "+android.text.format.Formatter.formatFileSize(this,result.bytes)+"\n\nGoogle Drive에 저장을 눌러 계정과 폴더를 선택한 뒤 저장하세요. 업로드 진행과 완료 여부는 Google Drive에서 확인합니다. 공유창 취소 시 업로드하지 않습니다.");}
    Intent sendIntent(){Uri uri=ExportProvider.uri(this,result.token);Intent intent=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,result.name).putExtra(Intent.EXTRA_TITLE,result.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri(result.name,uri));return intent;}
    void send(boolean direct){if(result==null||busy)return;Intent intent=sendIntent();if(direct)intent.setPackage("com.google.android.apps.docs");try{startActivity(direct?intent:Intent.createChooser(intent,"ZIP 저장·공유"));}catch(ActivityNotFoundException e){Toast.makeText(this,"Google Drive 앱이 없거나 이 공유를 지원하지 않습니다. 공유창에서 저장 위치를 선택하세요",1).show();try{startActivity(Intent.createChooser(sendIntent(),"Google Drive 또는 다른 저장 앱 선택"));}catch(ActivityNotFoundException none){Toast.makeText(this,"공유할 앱이 없습니다. 파일로 저장을 사용하세요",1).show();}}}
    void saveAs(){if(result!=null&&!busy)startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,result.name),20);}
    protected void onActivityResult(int request,int code,Intent data){super.onActivityResult(request,code,data);if(request!=20||code!=RESULT_OK||data==null||data.getData()==null||result==null)return;
        Uri target=data.getData();File source=new File(ExportProvider.directory(this),result.token+".zip");busy=true;cancelled.set(false);cancel.setEnabled(true);create.setEnabled(false);drive.setEnabled(false);share.setEnabled(false);save.setEnabled(false);status.setText("선택한 저장 위치로 ZIP 복사 중…");
        io.execute(()->{String message;try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(target,"w")){if(out==null)throw new IOException();byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){ZipExport.check(cancelled);out.write(buffer,0,n);}out.flush();message="선택한 저장소에 ZIP을 전달했습니다. Google Drive 업로드 상태는 Drive 앱에서 확인하세요.";}catch(Exception e){message="저장 또는 복사가 중단되었습니다. 선택한 저장소에 미완료 파일이 남았는지 확인하고 다시 시도하세요.";}final String text=message;main.post(()->{finished();status.setText(text);});});
    }
    protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(result!=null){b.putString("token",result.token);b.putString("name",result.name);b.putLong("bytes",result.bytes);b.putInt("count",result.count);}}
    public void onBackPressed(){if(busy){new AlertDialog.Builder(this).setMessage("처리 중입니다. 압축 중이면 취소한 뒤 종료할까요?").setPositiveButton("취소 후 나가기",(d,w)->{cancelled.set(true);finish();}).setNegativeButton("계속 처리",null).show();}else super.onBackPressed();}
    protected void onDestroy(){if(isFinishing()){cancelled.set(true);io.shutdown();}super.onDestroy();}
}
