package kr.folderalarm;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
final class ImageFingerprint {
    static String hash(Context c,Uri uri)throws Exception{
        Bitmap bitmap;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)throw new IOException("이 형식은 정확한 이미지 비교를 지원하지 않습니다");
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(ByteBuffer.allocate(8).putInt(bitmap.getWidth()).putInt(bitmap.getHeight()).array());
            int[] pixels=new int[bitmap.getWidth()];ByteBuffer row=ByteBuffer.allocate(pixels.length*4);
            for(int y=0;y<bitmap.getHeight();y++){bitmap.getPixels(pixels,0,pixels.length,0,y,pixels.length,1);row.clear();for(int value:pixels)row.putInt(value);digest.update(row.array());}
            StringBuilder text=new StringBuilder();for(byte value:digest.digest())text.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return text.toString();
        }finally{bitmap.recycle();}
    }
}
