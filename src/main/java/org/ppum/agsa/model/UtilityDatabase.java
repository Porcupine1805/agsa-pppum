package org.ppum.agsa.model;

import java.util.*;

public final class UtilityDatabase {
    private final List<Transaction> transactions;
    private final LinkedHashMap<String,Long> profits;
    public UtilityDatabase(List<Transaction> transactions,Map<String,Long> profits){
        Objects.requireNonNull(transactions,"transactions");
        Objects.requireNonNull(profits,"profits");
        this.transactions=new ArrayList<>(transactions.size());
        for(int i=0;i<transactions.size();i++){
            Transaction t=Objects.requireNonNull(transactions.get(i),"transaction "+(i+1));
            int expected=i+1;
            if(t.tid()!=expected)throw new IllegalArgumentException(
                    "TIDs must be unique, strictly increasing, and contiguous from 1; expected T"+expected+" but found T"+t.tid());
            this.transactions.add(t);
        }
        this.profits=new LinkedHashMap<>(profits);
        for(var e:this.profits.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue()<=0)
            throw new IllegalArgumentException("External utilities must be positive: "+e);
    }
    public List<Transaction> transactions(){return transactions;}
    public Map<String,Long> profits(){return Collections.unmodifiableMap(profits);}
    public int size(){return transactions.size();}
    public Transaction transactionByTid(int tid){
        if(tid<1||tid>transactions.size())throw new IllegalArgumentException("Invalid TID "+tid);
        Transaction t=transactions.get(tid-1);if(t.tid()==tid)return t;
        for(Transaction x:transactions)if(x.tid()==tid)return x;throw new IllegalArgumentException("TID not found: "+tid);
    }
    public long profit(String item){Long v=profits.get(item);if(v==null||v<=0)throw new IllegalArgumentException("Missing/non-positive external utility for "+item);return v;}
    public long totalUtility(){long s=0;for(Transaction t:transactions)s+=t.transactionUtility(profits);return s;}
    public UtilityDatabase copy(){ArrayList<Transaction> out=new ArrayList<>(transactions.size());for(Transaction t:transactions)out.add(t.copy());return new UtilityDatabase(out,profits);}
    public UtilityDatabase compactedTimeline(){ArrayList<Transaction> out=new ArrayList<>();int tid=1;for(Transaction t:transactions)if(!t.isEmpty())out.add(new Transaction(tid++,t.quantities()));return new UtilityDatabase(out,profits);}
    public long nonEmptyTransactions(){long n=0;for(Transaction t:transactions)if(!t.isEmpty())n++;return n;}
}
