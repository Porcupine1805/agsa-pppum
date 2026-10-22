package org.ppum.agsa.sanitize;
import org.ppum.agsa.model.*;import java.util.*;
public final class SmauSanitizer extends AbstractLiuSanitizer {
    private final boolean matchedPhui;
    public SmauSanitizer(){this(false);}
    public SmauSanitizer(boolean matchedPhui){this.matchedPhui=matchedPhui;}
    protected String name(){return matchedPhui?"SMAU-PHUI":"SMAU-REF";}
    protected boolean stopWhenNoLongerPhui(){return matchedPhui;}
    protected String chooseItem(ItemsetKey t,Transaction tx,UtilityDatabase db,PatternStateTracker tr,Set<ItemsetKey>n,PeriodicityConstraints c){String b=null;long v=Long.MIN_VALUE;for(String i:t.items())if(tx.contains(i)){long x=tx.itemUtility(i,db.profits());if(x>v||(x==v&&(b==null||i.compareTo(b)<0))){b=i;v=x;}}return b;}
}
