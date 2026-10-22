package org.ppum.agsa.sanitize;
import org.ppum.agsa.model.*;import java.util.*;
public final class SmiuSanitizer extends AbstractLiuSanitizer {
    protected String name(){return "SMIU-REF";}
    protected String chooseItem(ItemsetKey t,Transaction tx,UtilityDatabase db,PatternStateTracker tr,Set<ItemsetKey>n,PeriodicityConstraints c){String b=null;long v=Long.MAX_VALUE;for(String i:t.items())if(tx.contains(i)){long x=tx.itemUtility(i,db.profits());if(x<v||(x==v&&(b==null||i.compareTo(b)<0))){b=i;v=x;}}return b;}
}
