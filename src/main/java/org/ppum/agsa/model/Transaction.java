package org.ppum.agsa.model;

import java.util.*;

/**
 * Compact transaction representation. Item identifiers are stored once in a sorted
 * String array; quantities are stored in a primitive int array. Deletion sets a
 * quantity to zero rather than reallocating a map. This substantially reduces heap
 * pressure on million-transaction datasets.
 */
public final class Transaction {
    private final int tid;
    private final String[] items;
    private final int[] quantities;

    public Transaction(int tid, Map<String, Integer> input) {
        this.tid = tid;
        ArrayList<Map.Entry<String,Integer>> es = new ArrayList<>();
        for (var e : input.entrySet()) if (e.getValue() != null && e.getValue() > 0) es.add(e);
        es.sort(Map.Entry.comparingByKey());
        this.items = new String[es.size()];
        this.quantities = new int[es.size()];
        for (int i=0;i<es.size();i++) { items[i]=es.get(i).getKey(); quantities[i]=es.get(i).getValue(); }
    }

    public Transaction(int tid, String[] inputItems, int[] inputQuantities, int size) {
        this.tid = tid;
        int active=0; for(int i=0;i<size;i++) if(inputQuantities[i]>0) active++;
        String[] sortedItems=new String[active]; int[] sortedQuantities=new int[active];
        int p=0; for(int i=0;i<size;i++) if(inputQuantities[i]>0){sortedItems[p]=inputItems[i];sortedQuantities[p]=inputQuantities[i];p++;}
        // Transaction lengths are small in the target benchmarks; insertion sort avoids
        // allocating an Integer[] and millions of boxed index objects.
        for(int i=1;i<active;i++){String si=sortedItems[i];int qi=sortedQuantities[i],j=i-1;while(j>=0&&sortedItems[j].compareTo(si)>0){sortedItems[j+1]=sortedItems[j];sortedQuantities[j+1]=sortedQuantities[j];j--;}sortedItems[j+1]=si;sortedQuantities[j+1]=qi;}
        // Some upstream files repeat an item token in one transaction.  Such
        // tokens denote one database cell and must not create duplicate TIDs in
        // utility lists.  Canonicalize them by summing their quantities.
        int unique=0;
        for(int i=0;i<active;i++){
            if(unique>0&&sortedItems[unique-1].equals(sortedItems[i])){
                long sum=(long)sortedQuantities[unique-1]+sortedQuantities[i];
                if(sum>Integer.MAX_VALUE)throw new IllegalArgumentException("Quantity overflow for item "+sortedItems[i]+" in T"+tid);
                sortedQuantities[unique-1]=(int)sum;
            }else{
                sortedItems[unique]=sortedItems[i];sortedQuantities[unique]=sortedQuantities[i];unique++;
            }
        }
        this.items=Arrays.copyOf(sortedItems,unique);
        this.quantities=Arrays.copyOf(sortedQuantities,unique);
    }

    private Transaction(int tid, String[] items, int[] quantities, boolean trustedSorted) {
        this.tid=tid;
        this.items=items;
        this.quantities=quantities;
    }

    public int tid() { return tid; }
    public int storedItemSlots() { return items.length; }
    public int activeItemCount() { int n=0; for(int q:quantities) if(q>0)n++; return n; }
    public boolean isEmpty() { return activeItemCount()==0; }

    public String[] activeItemsArray() {
        String[] out=new String[activeItemCount()];int p=0;for(int i=0;i<items.length;i++)if(quantities[i]>0)out[p++]=items[i];return out;
    }

    public Iterable<String> activeItems() {
        return () -> new Iterator<>() {
            int p=advance(0);
            private int advance(int x){while(x<items.length&&quantities[x]<=0)x++;return x;}
            public boolean hasNext(){return p<items.length;}
            public String next(){if(!hasNext())throw new NoSuchElementException();String s=items[p];p=advance(p+1);return s;}
        };
    }

    /** Compatibility view; iteration skips zero-quantity slots. */
    public Set<String> items() {
        return new AbstractSet<>() {
            @Override public Iterator<String> iterator(){ return activeItems().iterator(); }
            @Override public int size(){ return activeItemCount(); }
            @Override public boolean contains(Object o){ return o instanceof String s && Transaction.this.contains(s); }
        };
    }

    /** Materialized only for serialization / compatibility, not in hot mining loops. */
    public Map<String,Integer> quantities() {
        LinkedHashMap<String,Integer> out=new LinkedHashMap<>();
        for(int i=0;i<items.length;i++) if(quantities[i]>0) out.put(items[i],quantities[i]);
        return Collections.unmodifiableMap(out);
    }

    private int indexOf(String item) { return Arrays.binarySearch(items,item); }
    public int quantity(String item) { int p=indexOf(item); return p<0?0:quantities[p]; }
    public boolean contains(String item) { return quantity(item)>0; }
    public boolean containsAll(ItemsetKey key) { for(String item:key.items()) if(!contains(item))return false; return true; }

    public void setQuantity(String item, int q) {
        int p=indexOf(item);
        if(p<0){ if(q>0) throw new IllegalArgumentException("Cannot add new item to decrease-only transaction: "+item); return; }
        quantities[p]=Math.max(0,q);
    }

    public long itemUtility(String item, Map<String, Long> profits) {
        int p=indexOf(item); if(p<0||quantities[p]<=0)return 0L;
        return (long)quantities[p]*profits.getOrDefault(item,0L);
    }

    public long itemsetUtility(ItemsetKey key, Map<String, Long> profits) {
        long sum=0L;
        for(String item:key.items()){
            int p=indexOf(item); if(p<0||quantities[p]<=0)return 0L;
            sum+=(long)quantities[p]*profits.getOrDefault(item,0L);
        }
        return sum;
    }

    public long transactionUtility(Map<String, Long> profits) {
        long sum=0L;
        for(int i=0;i<items.length;i++) if(quantities[i]>0) sum+=(long)quantities[i]*profits.getOrDefault(items[i],0L);
        return sum;
    }

    public int changedCellsAgainst(Transaction other) {
        int i=0,j=0,c=0;
        while(i<items.length||j<other.items.length){
            if(i>=items.length){ if(other.quantities[j]>0)c++; j++; continue; }
            if(j>=other.items.length){ if(quantities[i]>0)c++; i++; continue; }
            int cmp=items[i].compareTo(other.items[j]);
            if(cmp==0){ if(quantities[i]!=other.quantities[j])c++; i++;j++; }
            else if(cmp<0){ if(quantities[i]>0)c++; i++; }
            else { if(other.quantities[j]>0)c++; j++; }
        }
        return c;
    }

    public Transaction copy(){ return new Transaction(tid,items.clone(),quantities.clone(),true); }

    @Override public String toString(){
        StringBuilder sb=new StringBuilder("T").append(tid).append(' '); boolean first=true;
        for(int i=0;i<items.length;i++)if(quantities[i]>0){if(!first)sb.append(' ');sb.append(items[i]).append(':').append(quantities[i]);first=false;}
        return sb.toString().trim();
    }
}
