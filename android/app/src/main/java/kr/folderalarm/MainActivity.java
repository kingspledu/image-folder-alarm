package kr.folderalarm;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.widget.*;

public class MainActivity extends Activity {
    TextView status;
    EditText bookName;
    Button startButton, stopButton;
    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable update = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 2000); } };
    void refresh() {
        android.content.SharedPreferences p = getSharedPreferences("watch", 0);
        boolean running=WatchService.running;
        if(startButton!=null) startButton.setEnabled(!running);
        if(stopButton!=null) stopButton.setEnabled(running);
        if(bookName!=null) bookName.setEnabled(!running);
        status.setText((running?"● 감시 켜짐\n":"○ 감시 꺼짐\n") + "폴더: " + p.getString("folder", "선택하지 않음") + "\n\n" + p.getString("status", "감시 중지")
            + "\n\n새 이미지 이름: 책이름_001.확장자\n기존 파일은 변경하지 않습니다. 하위 폴더는 제외합니다.\n3초마다 검사하며 절전 중에는 지연될 수 있습니다.");
    }
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(1); layout.setPadding(36, 50, 36, 30);
        TextView title = new TextView(this); title.setText("이미지 폴더 알림"); title.setTextSize(27); layout.addView(title);
        status = new TextView(this); status.setTextSize(16); status.setPadding(0, 24, 0, 24); layout.addView(status);
        bookName=new EditText(this); bookName.setSingleLine(true);
        bookName.setHint("책 이름 (예: 어린 왕자)");
        bookName.setText(getSharedPreferences("watch",0).getString("bookName","")); layout.addView(bookName);
        button(layout, "1. 폴더 선택", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 10));
        startButton=button(layout, "감시 시작", () -> {
            String book=bookName.getText().toString().trim();
            if(!NamePolicy.validBook(book)) { Toast.makeText(this,"책 이름을 1~80자로 입력하고 파일명 금지 문자를 제외하세요",1).show();return; }
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 11); return;
            }
            if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); return;
            }
            if (!getSharedPreferences("watch", 0).contains("uri")) { Toast.makeText(this, "폴더를 먼저 선택하세요", 0).show(); return; }
            getSharedPreferences("watch",0).edit().putString("bookName",book).apply();
            startForegroundService(new Intent(this, WatchService.class).putExtra("bookName",book));
        });
        stopButton=button(layout, "감시 중지", () -> { stopService(new Intent(this, WatchService.class)); getSharedPreferences("watch", 0).edit().putString("status", "감시 중지").apply(); refresh(); });
        button(layout,"마지막 번호 이미지 한 장 삭제",()->DeleteLast.ask(this,bookName.getText().toString().trim()));
        button(layout, "배터리 설정", () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        ScrollView scroll = new ScrollView(this); scroll.addView(layout);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        setContentView(scroll);
    }
    Button button(LinearLayout layout, String text, Runnable action) { Button b = new Button(this); b.setText(text); b.setOnClickListener(v -> action.run()); layout.addView(b); return b; }
    protected void onResume() { super.onResume(); handler.post(update); }
    protected void onPause() { handler.removeCallbacks(update); super.onPause(); }
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null) return;
        int access = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((access & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) { Toast.makeText(this, "이름 변경을 위해 쓰기 가능한 폴더를 선택하세요", 1).show(); return; }
        try {
            Uri uri = data.getData(); getContentResolver().takePersistableUriPermission(uri, access);
            stopService(new Intent(this, WatchService.class));
            getSharedPreferences("watch", 0).edit().putString("uri", uri.toString()).putString("folder", Uri.decode(uri.getLastPathSegment()))
                .putString("status", "폴더 선택 완료 · 시작 버튼을 누르세요").apply(); refresh();
        } catch (Exception e) { Toast.makeText(this, "폴더 권한을 저장하지 못했습니다", 1).show(); }
    }
}
