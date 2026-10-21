package kr.folderalarm;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public class ExportProvider extends ContentProvider {
    static File directory(Context c){return new File(c.getFilesDir(),"exports");}
    static Uri uri(Context c,String token){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".exports").appendPath("exports").appendPath(token+".zip").build();}
    File file(Uri uri)throws FileNotFoundException{
        if(!"content".equals(uri.getScheme())||!(getContext().getPackageName()+".exports").equals(uri.getAuthority())||uri.getPathSegments().size()!=2||!"exports".equals(uri.getPathSegments().get(0)))throw new FileNotFoundException();
        String name=uri.getLastPathSegment();if(name==null||!name.matches("[a-f0-9]{32}\\.zip"))throw new FileNotFoundException();
        File file=new File(directory(getContext()),name);
        try{if(!file.getCanonicalFile().getParentFile().equals(directory(getContext()).getCanonicalFile())||!file.isFile())throw new FileNotFoundException();}catch(IOException e){throw new FileNotFoundException();}return file;
    }
    public boolean onCreate(){return true;}
    public String getType(Uri uri){try{file(uri);return "application/zip";}catch(FileNotFoundException e){return null;}}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        try{File file=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(cols);Object[] row=new Object[cols.length];
            String token=uri.getLastPathSegment().replace(".zip","");String display=getContext().getSharedPreferences("watch",0).getString("exportName:"+token,"captures.zip");
            for(int i=0;i<cols.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?display:OpenableColumns.SIZE.equals(cols[i])?file.length():null;cursor.addRow(row);return cursor;
        }catch(FileNotFoundException e){return null;}
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("읽기만 허용됩니다");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
