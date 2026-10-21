package kr.folderalarm;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

final class ZipExport {
    interface Progress{void show(String text);}
    static final class Page {String uri,name;long number,size,modified;Page(String u,String n,long p,long s,long m){uri=u;name=n;number=p;size=s;modified=m;}}
    static final class Result {String token,name;long bytes;int count;Result(String t,String n,long b,int c){token=t;name=n;bytes=b;count=c;}}
    static List<Page> pages(Context c,String folder,String book)throws Exception{
        Uri tree=Uri.parse(folder);List<Page> pages=new ArrayList<>();Set<Long> numbers=new HashSet<>();
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        try(Cursor cursor=c.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),columns,null,null,null)){
            if(cursor==null)throw new IOException("폴더를 읽지 못했습니다");while(cursor.moveToNext()){
                String name=cursor.getString(1);if(DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))||!ZipTools.include(book,name))continue;
                long number=NamePolicy.number(book,name);if(!numbers.add(number))throw new IOException("같은 번호의 파일이 여러 개입니다. 먼저 페이지를 보정하세요: "+number);
                pages.add(new Page(DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)).toString(),name,number,cursor.isNull(3)?-1:cursor.getLong(3),cursor.isNull(4)?0:cursor.getLong(4)));
            }
        }pages.sort(Comparator.comparingLong(p->p.number));return pages;
    }
    static void check(AtomicBoolean cancelled)throws InterruptedIOException{if(cancelled.get())throw new InterruptedIOException("압축을 취소했습니다");}
    static String hex(byte[] bytes){StringBuilder text=new StringBuilder();for(byte b:bytes)text.append(String.format(Locale.ROOT,"%02x",b&255));return text.toString();}
    static Result create(Context c,String folder,String book,AtomicBoolean cancelled,Progress progress)throws Exception{
        if(!NamePolicy.validBook(book)||folder.isEmpty())throw new IOException("책 이름과 폴더를 먼저 선택하세요");
        String token=UUID.randomUUID().toString().replace("-","");File dir=ExportProvider.directory(c);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("압축 저장 공간을 만들지 못했습니다");
        File partial=new File(dir,token+".partial"),complete=new File(dir,token+".zip");
        try{synchronized(WatchService.FILE_LOCK){
            if(!CaptureRepair.prefs(c).getString("repairJournal","").isEmpty())throw new IOException("중단된 페이지 보정을 먼저 복구하세요");
            JSONObject session=CaptureRepair.session(c);if(session.optJSONArray("shots")!=null&&session.optJSONArray("shots").length()>0)throw new IOException("재캡처 묶음을 먼저 분석·적용하거나 취소하세요");
            if(WatchService.live!=null&&(WatchService.live.known==null||!WatchService.live.pending.isEmpty()))throw new IOException("캡처 정리가 끝난 뒤 다시 압축하세요");
            List<Page> pages=pages(c,folder,book);if(pages.isEmpty())throw new IOException("현재 책 이름으로 번호가 붙은 이미지가 없습니다");
            Set<String> expected=new HashSet<>();JSONArray files=new JSONArray();byte[] buffer=new byte[65536];
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(partial)))){
                zip.setLevel(Deflater.BEST_SPEED);
                for(int i=0;i<pages.size();i++){
                    check(cancelled);Page page=pages.get(i);progress.show("압축 중 "+(i+1)+" / "+pages.size()+"장\n"+page.name);zip.putNextEntry(new ZipEntry(page.name));MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
                    try(InputStream input=c.getContentResolver().openInputStream(Uri.parse(page.uri))){if(input==null)throw new IOException("파일 읽기 실패: "+page.name);int n;while((n=input.read(buffer))!=-1){check(cancelled);zip.write(buffer,0,n);digest.update(buffer,0,n);size+=n;}}
                    zip.closeEntry();if(page.size>=0&&size!=page.size)throw new IOException("압축 중 이미지가 변경되었습니다: "+page.name);
                    expected.add(page.name);files.put(new JSONObject().put("name",page.name).put("page",page.number).put("bytes",size).put("sha256",hex(digest.digest())));
                }
                JSONObject manifest=new JSONObject().put("book",book).put("count",pages.size()).put("created_at",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.ROOT).format(new Date())).put("files",files);
                zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();expected.add("manifest.json");
            }
            progress.show("ZIP 파일 무결성 확인 중…");ZipTools.verify(partial,expected,cancelled::get);
            List<Page> after=pages(c,folder,book);if(after.size()!=pages.size())throw new IOException("압축 중 파일 목록이 바뀌었습니다. 다시 시도하세요");
            for(int i=0;i<pages.size();i++){Page a=pages.get(i),b=after.get(i);if(!a.uri.equals(b.uri)||!a.name.equals(b.name)||a.size!=b.size||a.modified!=b.modified)throw new IOException("압축 중 파일이 바뀌었습니다. 다시 시도하세요");}
            check(cancelled);if(!partial.renameTo(complete))throw new IOException("검증된 ZIP을 저장하지 못했습니다");
            String name=book+"_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+"_"+token.substring(0,6)+".zip";
            if(!CaptureRepair.prefs(c).edit().putString("exportName:"+token,name).commit())throw new IOException("ZIP 파일명 기록을 저장하지 못했습니다");return new Result(token,name,complete.length(),pages.size());
        }}finally{if(partial.isFile())partial.delete();}
    }
}
