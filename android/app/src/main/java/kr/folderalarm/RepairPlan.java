package kr.folderalarm;
import java.util.*;
final class RepairPlan {
    static final class Page {final String uri,name,hash;Page(String u,String n,String h){uri=u;name=n;hash=h;}}
    final List<Page> before,staged,after;final String summary;
    final List<Long> numbers=new ArrayList<>();
    final Map<String,Long> anchors=new HashMap<>();
    int step=1;
    RepairPlan(List<Page>b,List<Page>s,List<Page>a,String text){before=b;staged=s;after=a;summary=text;for(int i=0;i<a.size();i++)numbers.add((long)i+1);}
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
    static RepairPlan align(List<Page> old,List<Page> shots,int step){PageStride.check(step);RepairPlan p=align(old,shots);p.step=step;for(int i=0;i<p.numbers.size();i++)p.numbers.set(i,PageStride.advance(1,i,step));return p;}
    static RepairPlan anchored(List<Page> old,List<Page> shots,String book,long start,Map<String,Long> trusted){return anchored(old,shots,book,start,trusted,1);}
    static RepairPlan anchored(List<Page> old,List<Page> shots,String book,long start,Map<String,Long> trusted,int step){
        PageStride.check(step);
        if(start<1||start>1000000000L)throw new IllegalArgumentException("시작 페이지는 1~1000000000 사이로 지정하세요");
        List<Page> base=new ArrayList<>(),incoming=new ArrayList<>();Set<String> seen=new HashSet<>();for(Page p:old)if(seen.add(p.hash))base.add(p);
        seen.clear();for(Page p:shots)if(seen.add(p.hash))incoming.add(p);
        if(incoming.isEmpty())throw new IllegalArgumentException("새 캡처가 없습니다");
        Map<String,Integer> at=new HashMap<>();for(int i=0;i<base.size();i++)at.put(base.get(i).hash,i);
        int previous=-1,matches=0;
        for(int i=0;i<incoming.size();i++){
            Page p=incoming.get(i);Long reliable=trusted.get(p.hash);
            if(reliable!=null&&reliable.longValue()!=PageStride.advance(start,i,step))throw new IllegalArgumentException("이전에 지정한 페이지 기준과 충돌합니다. 시작 페이지나 캡처 순서를 확인하세요");
            Integer position=at.get(p.hash);if(position==null)continue;
            if(position<=previous)throw new IllegalArgumentException("기존 이미지와 재캡처의 순서가 충돌합니다");previous=position;matches++;
        }
        long max=0;for(Page p:base)max=Math.max(max,NamePolicy.number(book,p.name));
        int left,right;
        if(matches==0&&(base.isEmpty()||start>max)){left=base.size();right=left;}
        else {
            Integer first=at.get(incoming.get(0).hash),last=at.get(incoming.get(incoming.size()-1).hash);
            if(first==null||last==null)throw new IllegalArgumentException("중간 교체는 첫 장과 마지막 장이 기존 이미지와 일치해야 합니다. 앞뒤를 더 포함하고 실제 첫 페이지 번호부터 지정하세요");
            left=first;right=last+1;
        }
        List<Page> result=new ArrayList<>();List<Long> assigned=new ArrayList<>();long prior=0;
        for(Page p:base.subList(0,left)){
            long n=trusted.containsKey(p.hash)?trusted.get(p.hash):NamePolicy.number(book,p.name);
            if(n<=prior||n>=start)throw new IllegalArgumentException("앞쪽 파일 번호와 지정 페이지가 충돌합니다. 앞쪽 구간부터 먼저 보정하세요");result.add(p);assigned.add(n);prior=n;
        }
        for(int i=0;i<incoming.size();i++){result.add(incoming.get(i));assigned.add(PageStride.advance(start,i,step));}
        long next=PageStride.advance(start,incoming.size(),step);
        for(Page p:base.subList(right,base.size())){
            Long reliable=trusted.get(p.hash);long n=reliable==null?next:reliable;
            if(n<next)throw new IllegalArgumentException("뒤쪽의 확정 페이지와 번호가 충돌합니다. 빠진 이미지나 잘못된 시작 번호를 확인하세요");result.add(p);assigned.add(n);next=PageStride.advance(n,1,step);
        }
        RepairPlan plan=new RepairPlan(new ArrayList<>(old),new ArrayList<>(shots),result,"지정 시작 "+start+"페이지 · 기존과 같은 이미지 "+matches+"장\n새 묶음 "+shots.size()+"장 → 중복 제외 "+incoming.size()+"장\n재캡처에 "+start+"~"+PageStride.advance(start,incoming.size()-1,step)+"을 배정하고 뒤쪽 번호를 추론했습니다.\n앞쪽 미확정 번호는 보존합니다. 순서를 확인한 뒤 적용하세요. 교체·중복 원본은 백업합니다.");
        plan.numbers.clear();plan.numbers.addAll(assigned);for(int i=0;i<incoming.size();i++)plan.anchors.put(incoming.get(i).hash,PageStride.advance(start,i,step));plan.step=step;return plan;
    }
    static RepairPlan dedupe(List<Page> old,String book){
        RepairPlan compact=dedupe(old);RepairPlan result=new RepairPlan(compact.before,compact.staged,compact.after,"정확히 같은 중복 "+(old.size()-compact.after.size())+"장. 첫 이미지를 유지하고 나머지는 보관합니다.\n실제 페이지 기준을 잃지 않도록 현재 번호는 유지합니다. 전체 번호 보정은 시작 페이지 지정 후 구간 분석을 사용하세요.");result.numbers.clear();for(Page p:result.after)result.numbers.add(NamePolicy.number(book,p.name));return result;
    }
    static RepairPlan dedupe(List<Page> old){
        List<Page> result=new ArrayList<>();Set<String> seen=new HashSet<>();for(Page p:old)if(seen.add(p.hash))result.add(p);
        return new RepairPlan(new ArrayList<>(old),Collections.emptyList(),result,"정확히 같은 이미지 중복 "+(old.size()-result.size())+"장\n전체 "+old.size()+"장 → "+result.size()+"장\n첫 이미지를 유지합니다. 같은 그림이 의도적으로 반복된 페이지인지 확인하세요. 적용 후 번호는 001부터 다시 맞춥니다.");
    }
}
