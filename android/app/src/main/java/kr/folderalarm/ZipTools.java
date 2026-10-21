package kr.folderalarm;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;
final class ZipTools {
    static boolean safeName(String name){return name!=null&&!name.isEmpty()&&!name.equals(".")&&!name.equals("..")&&name.indexOf('/')<0&&name.indexOf('\\')<0&&name.indexOf((char)0)<0;}
    static boolean include(String book,String name){return safeName(name)&&NamePolicy.number(book,name)>0;}
    static void verify(File file,Set<String> expected,BooleanSupplier cancelled)throws Exception{
        Set<String> found=new HashSet<>();byte[] buffer=new byte[65536];
        try(ZipFile zip=new ZipFile(file)){
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();if(entry.isDirectory()||!safeName(entry.getName())||!found.add(entry.getName()))throw new IOException("ZIP 항목이 올바르지 않습니다");
                CRC32 crc=new CRC32();long size=0;try(InputStream in=zip.getInputStream(entry)){int count;while((count=in.read(buffer))!=-1){if(cancelled.getAsBoolean())throw new InterruptedIOException("취소했습니다");crc.update(buffer,0,count);size+=count;}}
                if(crc.getValue()!=entry.getCrc()||size!=entry.getSize())throw new IOException("ZIP 검증 실패: "+entry.getName());
            }
        }
        if(!found.equals(expected))throw new IOException("ZIP의 파일 목록이 압축 대상과 다릅니다");
    }
}
