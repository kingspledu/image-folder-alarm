package kr.folderalarm;
import java.util.*;
import java.util.regex.*;
public final class NamePolicy {
    private NamePolicy() {}
    public static boolean validBook(String book) {
        return book != null && !book.trim().isEmpty() && book.length() <= 80
            && !book.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*") && !book.endsWith(".");
    }
    public static long number(String book, String name) {
        Matcher m=Pattern.compile(Pattern.quote(book)+"_([0-9]{3,})\\.(png|jpe?g|webp|gif|bmp|heic|heif|avif)",Pattern.CASE_INSENSITIVE).matcher(name);
        if(!m.matches()) return -1;
        try { return Long.parseLong(m.group(1)); } catch(NumberFormatException e) { return -1; }
    }
    public static String next(String book,String original,Collection<String> names) {return next(book,original,names,1);}
    public static String next(String book,String original,Collection<String> names,int step) {
        long max=0;
        for(String name:names) max=Math.max(max,number(book,name));
        if(max==Long.MAX_VALUE) throw new IllegalStateException("Number limit reached");
        int dot=original.lastIndexOf('.'); String ext=dot>=0?original.substring(dot):"";
        return book+"_"+String.format(Locale.ROOT,"%03d",PageStride.after(max,step))+ext;
    }
}
