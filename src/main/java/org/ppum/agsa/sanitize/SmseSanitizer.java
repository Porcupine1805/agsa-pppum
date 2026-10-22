package org.ppum.agsa.sanitize;
import org.ppum.agsa.model.*;import java.util.*;
public final class SmseSanitizer extends AbstractLiuSanitizer {
    private final boolean matchedPhui;
    public SmseSanitizer(){this(false);}
    public SmseSanitizer(boolean matchedPhui){this.matchedPhui=matchedPhui;}
    protected String name(){return matchedPhui?"SMSE-PHUI":"SMSE-REF";}
    protected boolean stopWhenNoLongerPhui(){return matchedPhui;}
    protected String chooseItem(ItemsetKey t,Transaction tx,UtilityDatabase db,PatternStateTracker tr,Set<ItemsetKey>n,PeriodicityConstraints c){String b=null;int best=Integer.MAX_VALUE;long util=Long.MAX_VALUE;for(String i:t.items())if(tx.contains(i)){int se=sideEffectCount(i,tx.tid(),db,tr,n,c);long u=tx.itemUtility(i,db.profits());if(se<best||(se==best&&u<util)||(se==best&&u==util&&(b==null||i.compareTo(b)<0))){b=i;best=se;util=u;}}return b;}
}
