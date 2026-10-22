package org.ppum.agsa.mining;

import org.ppum.agsa.model.*;
import java.util.*;

/**
 * Self-contained HUI-Miner-style positive-utility miner with primitive utility lists.
 * It mines the complete HUI witness universe and labels witnesses as PHUI/non-PHUI.
 */
public final class VerticalHuiMiner {
    private static final class UList {
        final String item;
        int[] tids=new int[8]; long[] iutils=new long[8], rutils=new long[8]; int size;
        long sumI,sumR;
        UList(String item){this.item=item;}
        void add(int tid,long iu,long ru){
            if(size==tids.length){int n=size<1024?size*2:size+(size>>1);tids=Arrays.copyOf(tids,n);iutils=Arrays.copyOf(iutils,n);rutils=Arrays.copyOf(rutils,n);}
            tids[size]=tid;iutils[size]=iu;rutils[size]=ru;size++;sumI+=iu;sumR+=ru;
        }
        int tid(int i){return tids[i];} long iu(int i){return iutils[i];} long ru(int i){return rutils[i];}
        PrimitiveIntList tidList(){return PrimitiveIntList.copyOf(tids,size);}
    }

    private UtilityDatabase db; private PeriodicityConstraints c; private int maxLen;
    private final LinkedHashMap<ItemsetKey,PatternInfo> huis=new LinkedHashMap<>();

    public Map<ItemsetKey,PatternInfo> mine(UtilityDatabase db,PeriodicityConstraints c){return mine(db,c,0);}
    public Map<ItemsetKey,PatternInfo> mine(UtilityDatabase db,PeriodicityConstraints c,int maxLen){
        this.db=db;this.c=c;this.maxLen=maxLen;huis.clear();
        Map<String,Long> twu=new HashMap<>(Math.max(16,db.profits().size()*2));
        for(Transaction t:db.transactions()){
            long tu=t.transactionUtility(db.profits());
            for(String item:t.activeItems())twu.merge(item,tu,Long::sum);
        }
        ArrayList<String> promising=new ArrayList<>();
        for(var e:twu.entrySet())if(e.getValue()>=c.minUtil())promising.add(e.getKey());
        promising.sort(Comparator.<String>comparingLong(twu::get).thenComparing(Comparator.naturalOrder()));
        Map<String,Integer> order=new HashMap<>(Math.max(16,promising.size()*2));
        LinkedHashMap<String,UList> singles=new LinkedHashMap<>();
        for(int i=0;i<promising.size();i++){String item=promising.get(i);order.put(item,i);singles.put(item,new UList(item));}

        for(Transaction t:db.transactions()){
            ArrayList<String> kept=new ArrayList<>(t.activeItemCount());
            for(String item:t.activeItems())if(order.containsKey(item))kept.add(item);
            kept.sort(Comparator.comparingInt(order::get));
            long remaining=0;for(String item:kept)remaining+=t.itemUtility(item,db.profits());
            for(String item:kept){long iu=t.itemUtility(item,db.profits());remaining-=iu;singles.get(item).add(t.tid(),iu,remaining);}
        }
        search(new ArrayList<>(),null,new ArrayList<>(singles.values()));
        return new LinkedHashMap<>(huis);
    }

    private void search(List<String> prefix,UList prefixUL,List<UList> exts){
        for(int i=0;i<exts.size();i++){
            UList x=exts.get(i);ArrayList<String>xItems=new ArrayList<>(prefix);xItems.add(x.item);
            if(x.sumI>=c.minUtil()){
                ItemsetKey key=new ItemsetKey(xItems);PrimitiveIntList tids=x.tidList();
                PeriodicityStats ps=PeriodicityStats.fromTids(tids,db.size());
                huis.put(key,new PatternInfo(key,x.sumI,ps,true,c.accepts(ps),tids));
            }
            if((maxLen<=0||xItems.size()<maxLen)&&x.sumI+x.sumR>=c.minUtil()){
                ArrayList<UList> next=new ArrayList<>();
                for(int j=i+1;j<exts.size();j++){
                    UList xy=construct(prefixUL,x,exts.get(j));
                    // Keep every nonempty join operand.  A low-bound list will
                    // not open its own recursive branch, but it may still be
                    // required as the right operand that constructs a high-
                    // utility superset from an earlier extension.
                    if(xy.size>0)next.add(xy);
                }
                if(!next.isEmpty())search(xItems,x,next);
            }
        }
    }

    private static UList construct(UList p,UList x,UList y){
        UList out=new UList(y.item);int ix=0,iy=0,ip=0;
        while(ix<x.size&&iy<y.size){
            int tx=x.tid(ix),ty=y.tid(iy);if(tx<ty){ix++;continue;}if(ty<tx){iy++;continue;}
            long pu=0;if(p!=null){while(ip<p.size&&p.tid(ip)<tx)ip++;if(ip>=p.size||p.tid(ip)!=tx){ix++;iy++;continue;}pu=p.iu(ip);}
            out.add(tx,x.iu(ix)+y.iu(iy)-pu,y.ru(iy));ix++;iy++;
        }
        return out;
    }
}
