package kr.folderalarm;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.Toast;
import java.util.concurrent.*;
final class DeleteLast {
    static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    static boolean busy;
    static class Entry { Uri uri;String name;long number;int flags;Entry(Uri u,String n,long i,int f){uri=u;name=n;number=i;flags=f;} }
    static void message(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
    static void ask(Activity a,String book) {
        if(!NamePolicy.validBook(book)){message(a,"책 이름을 먼저 입력하세요");return;}
        String folder=a.getSharedPreferences("watch",0).getString("uri","");
        if(folder.isEmpty()){message(a,"폴더를 먼저 선택하세요");return;}
        if(busy)return;busy=true;
        WORK.execute(()->{
            try {
                Entry last;
                synchronized(WatchService.FILE_LOCK){last=find(a,folder,book);}
                final Entry selected=last;
                a.runOnUiThread(()->{
                    busy=false;if(a.isFinishing()||a.isDestroyed())return;
                    if(selected==null){message(a,"이 책 이름으로 저장된 이미지가 없습니다");return;}
                    if((selected.flags&DocumentsContract.Document.FLAG_SUPPORTS_DELETE)==0){message(a,"이 폴더는 삭제를 지원하지 않습니다");return;}
                    new AlertDialog.Builder(a).setTitle("마지막 번호 이미지 삭제")
                        .setMessage(selected.name+"\n\n이 파일 한 장을 영구 삭제할까요? 휴지통으로 이동하지 않습니다.")
                        .setNegativeButton("취소",null).setPositiveButton("한 장 삭제",(d,w)->remove(a,folder,book,selected)).show();
                });
            }catch(Exception e){a.runOnUiThread(()->{busy=false;message(a,"폴더 접근 권한을 확인하세요");});}
        });
    }
    static Entry find(Context a,String folder,String book)throws Exception {
        Uri tree=Uri.parse(folder);Entry last=null;
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        String[] cols={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_MIME_TYPE};
        try(Cursor c=a.getContentResolver().query(children,cols,null,null,null)){
            if(c==null)throw new IllegalStateException();
            while(c.moveToNext()){
                String name=c.getString(1);if(name==null||DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(3)))continue;
                long n=NamePolicy.number(book,name);if(n<1)continue;
                if(last==null||n>last.number||n==last.number&&name.compareTo(last.name)>0)
                    last=new Entry(DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)),name,n,c.getInt(2));
            }
        }return last;
    }
    static void remove(Activity a,String folder,String book,Entry expected){
        if(busy)return;busy=true;
        WORK.execute(()->{
            String result;
            try{synchronized(WatchService.FILE_LOCK){
                Entry now=find(a,folder,book);
                if(now==null||!now.uri.equals(expected.uri)||!now.name.equals(expected.name))result="마지막 파일이 바뀌었습니다. 삭제 버튼을 다시 눌러 주세요";
                else if(DocumentsContract.deleteDocument(a.getContentResolver(),expected.uri))result="삭제 완료: "+expected.name;
                else result="삭제하지 못했습니다";
            }}catch(Exception e){result="삭제하지 못했습니다. 파일과 접근 권한을 확인하세요";}
            final String text=result;a.runOnUiThread(()->{busy=false;message(a,text);});
        });
    }
}
