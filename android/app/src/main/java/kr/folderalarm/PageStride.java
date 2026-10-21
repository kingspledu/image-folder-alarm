package kr.folderalarm;
final class PageStride {
    static int check(int step){if(step!=1&&step!=2)throw new IllegalArgumentException("페이지 간격은 1 또는 2여야 합니다");return step;}
    static long advance(long start,long count,int step){check(step);if(start<0||count<0)throw new IllegalArgumentException();return Math.addExact(start,Math.multiplyExact(count,(long)step));}
    static long after(long last,int step){check(step);return last==0?1:advance(last,1,step);}
}
