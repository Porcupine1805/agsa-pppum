package org.ppum.agsa.sanitize;

import org.ppum.agsa.model.*;
import java.util.*;

/** Dynamic singleton occurrence index for MU-MAP/MU-MIP without full rescans. */
final class OccurrenceIndex {
    private final Map<String,GapTracker> byItem=new HashMap<>();
    OccurrenceIndex(UtilityDatabase db){
        Map<String,ArrayList<Integer>> tmp=new HashMap<>();
        for(Transaction t:db.transactions())for(String item:t.activeItems())tmp.computeIfAbsent(item,x->new ArrayList<>()).add(t.tid());
        for(var e:tmp.entrySet())byItem.put(e.getKey(),new GapTracker(e.getValue(),db.size()));
    }
    int maxPer(String item){GapTracker g=byItem.get(item);return g==null?Integer.MAX_VALUE:g.stats().maxPeriod();}
    void commitDeletion(String item,int tid){GapTracker g=byItem.get(item);if(g!=null)g.remove(tid);}
}
