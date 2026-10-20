package kr.folderalarm;
import java.util.*;
final class RepairPlan {
    static final class Page {final String uri,name,hash;Page(String u,String n,String h){uri=u;name=n;hash=h;}}
    final List<Page> before,staged,after;final String summary;
    RepairPlan(List<Page>b,List<Page>s,List<Page>a,String text){before=b;staged=s;after=a;summary=text;}
    static RepairPlan align(List<Page> old,List<Page> shots){
        Map<String,Integer> positions=new HashMap<>();Set<String> ambiguous=new HashSet<>();
        for(int i=0;i<old.size();i++){String h=old.get(i).hash;if(positions.containsKey(h))ambiguous.add(h);else positions.put(h,i);}
        List<Page> unique=new ArrayList<>();Set<String> seen=new HashSet<>();for(Page p:shots)if(seen.add(p.hash))unique.add(p);
        int first=-1,last=-1,left=-1,right=-1,previous=-1;
        for(int i=0;i<unique.size();i++){
            String h=unique.get(i).hash;if(ambiguous.contains(h))throw new IllegalArgumentException("같은 이미지가 기존 책 안에 여러 번 있어 위치를 확정할 수 없습니다. 먼저 중복 검사를 확인하세요");
            Integer at=positions.get(h);if(at==null)continue;
            if(at<=previous)throw new IllegalArgumentException("재캡처 순서가 기존 페이지 순서와 다릅니다. 앞에서 뒤로 다시 캡처하세요");previous=at;
            if(first<0){first=i;left=at;}last=i;right=at;
        }
        if(first<0||last<=first||right<=left)throw new IllegalArgumentException("앞뒤 일치 이미지를 두 장 이상 찾지 못했습니다. 같은 화면 크기로 기존 앞뒤 페이지까지 겹쳐 다시 캡처하세요");
        List<Page> result=new ArrayList<>(old.subList(0,left));
        for(Page p:unique.subList(first,last+1)){Integer at=positions.get(p.hash);result.add(at==null?p:old.get(at));}
        result.addAll(old.subList(right+1,old.size()));
        return new RepairPlan(new ArrayList<>(old),new ArrayList<>(shots),result,"기존 파일 "+(left+1)+"~"+(right+1)+" 구간: "+(right-left+1)+"장 → "+(last-first+1)+"장\n전체 "+old.size()+"장 → "+result.size()+"장\n앞뒤 동일 이미지 기준으로 연결했습니다. 내용 순서를 확인한 뒤 적용하세요. 이후 파일 번호도 001부터 다시 맞춥니다.");
    }
    static RepairPlan dedupe(List<Page> old){
        List<Page> result=new ArrayList<>();Set<String> seen=new HashSet<>();for(Page p:old)if(seen.add(p.hash))result.add(p);
        return new RepairPlan(new ArrayList<>(old),Collections.emptyList(),result,"정확히 같은 이미지 중복 "+(old.size()-result.size())+"장\n전체 "+old.size()+"장 → "+result.size()+"장\n첫 이미지를 유지합니다. 같은 그림이 의도적으로 반복된 페이지인지 확인하세요. 적용 후 번호는 001부터 다시 맞춥니다.");
    }
}
