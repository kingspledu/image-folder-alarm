package kr.folderalarm;
final class DisplayNumber {
    static long value(long actual,int offset){
        if(offset>0&&actual>Long.MAX_VALUE-offset)return Long.MAX_VALUE;
        return Math.max(0,actual+(long)offset);
    }
}
