package kr.folderalarm;
import android.app.*;
import android.content.*;
import android.media.*;
import android.provider.Settings;
public final class Alerts {
    static void channels(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        NotificationChannel images=new NotificationChannel("images","새 이미지 알림",NotificationManager.IMPORTANCE_HIGH);
        images.setDescription("새 캡처 감지와 알림 테스트. 화면 팝업은 휴대폰 설정에 따릅니다.");
        images.enableVibration(true);
        images.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        nm.createNotificationChannel(images);
        nm.createNotificationChannel(new NotificationChannel("results","이름 변경 결과",NotificationManager.IMPORTANCE_LOW));
    }
    static String state(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(!nm.areNotificationsEnabled())return "알림 차단됨 · 알림 권한을 허용하세요";
        NotificationChannel ch=nm.getNotificationChannel("images");
        if(ch==null)return "알림 채널 준비 전";
        if(ch.getImportance()==NotificationManager.IMPORTANCE_NONE)return "새 이미지 알림 차단됨 · 팝업 알림 설정에서 허용하세요";
        if(ch.getImportance()<NotificationManager.IMPORTANCE_HIGH)return "알림이 조용하게 설정됨 · 팝업 알림 설정을 확인하세요";
        return "알림 허용됨 · 화면 팝업은 삼성 알림·방해 금지 설정에 따릅니다";
    }
    static void test(Context c){
        channels(c);

        c.getSystemService(NotificationManager.class).notify(4,new Notification.Builder(c,"images")
            .setSmallIcon(android.R.drawable.ic_menu_gallery).setContentTitle("이미지 폴더 알림 · 테스트")
            .setContentText("이 알림이 알림창에 있으면 권한은 정상입니다. 팝업은 휴대폰 설정을 확인하세요.")
            .setStyle(new Notification.BigTextStyle().bigText("이 알림이 알림창에 보이면 알림 권한은 정상입니다. 화면 위 팝업이 없다면 삼성 알림 설정에서 소리 및 팝업을 허용하고 방해 금지를 확인하세요."))
            .setAutoCancel(true).build());
    }
    static void settings(Context c){
        c.startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,c.getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,"images"));
    }
}
