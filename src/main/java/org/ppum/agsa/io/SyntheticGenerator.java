package org.ppum.agsa.io;

import org.ppum.agsa.model.*;
import java.util.*;

public final class SyntheticGenerator {
    private SyntheticGenerator() {}

    public static UtilityDatabase generate(int transactions, int items, double density, long seed) {
        Random r=new Random(seed);
        LinkedHashMap<String,Long> profits=new LinkedHashMap<>();
        for(int i=0;i<items;i++) profits.put("i"+(i+1),(long)(1+r.nextInt(20)));
        List<Transaction> txs=new ArrayList<>();
        for(int tid=1;tid<=transactions;tid++){
            LinkedHashMap<String,Integer> q=new LinkedHashMap<>();
            for(String item:profits.keySet()) if(r.nextDouble()<density) q.put(item,1+r.nextInt(5));
            if(q.isEmpty()) {String item="i"+(1+r.nextInt(items));q.put(item,1+r.nextInt(5));}
            txs.add(new Transaction(tid,q));
        }
        return new UtilityDatabase(txs,profits);
    }
}
