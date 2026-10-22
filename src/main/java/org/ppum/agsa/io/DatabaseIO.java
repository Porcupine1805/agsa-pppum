package org.ppum.agsa.io;

import org.ppum.agsa.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fast streaming reader for DSI-Lab1/PPUM item_count and item_utility files. */
public final class DatabaseIO {
    private DatabaseIO() {}

    public static Map<String,Long> readProfits(Path path)throws IOException{
        LinkedHashMap<String,Long> out=new LinkedHashMap<>();
        try(BufferedReader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            String raw;while((raw=r.readLine())!=null){
                String line=strip(raw);if(line.isBlank())continue;
                StringTokenizer st=new StringTokenizer(line," \t,;:=");
                if(st.countTokens()<2)throw new IOException("Bad utility line: "+raw);
                String item=st.nextToken();long v=Long.parseLong(st.nextToken());
                if(v<=0)throw new IOException("AGSA hard-guard theory assumes positive external utilities: "+raw);
                if(out.putIfAbsent(item,v)!=null)throw new IOException("Duplicate external utility for item "+item);
            }
        }
        return out;
    }

    public static UtilityDatabase readSimple(Path database,Path utilityTable)throws IOException{return readSimple(database,utilityTable,0);}

    /** maxTransactions<=0 means full file. Strings are canonicalized to profit-table keys. */
    public static UtilityDatabase readSimple(Path database,Path utilityTable,int maxTransactions)throws IOException{
        Map<String,Long> profits=readProfits(utilityTable);
        Map<String,String> canonical=new HashMap<>(Math.max(16,profits.size()*2));
        for(String k:profits.keySet())canonical.put(k,k);
        ArrayList<Transaction> txs=maxTransactions>0?new ArrayList<>(maxTransactions):new ArrayList<>();
        int tid=1;
        try(BufferedReader r=new BufferedReader(Files.newBufferedReader(database,StandardCharsets.UTF_8),1<<20)){
            String raw;while((raw=r.readLine())!=null){
                if(maxTransactions>0&&tid>maxTransactions)break;
                String line=stripPreserveEmpty(raw);
                String[] items=new String[16];int[] qty=new int[16];int n=0;
                int p=0,L=line.length();
                while(p<L){
                    while(p<L&&Character.isWhitespace(line.charAt(p)))p++;if(p>=L)break;
                    int e=p;while(e<L&&!Character.isWhitespace(line.charAt(e)))e++;
                    String tok=line.substring(p,e);p=e;
                    int sep=tok.indexOf(':');if(sep<0)sep=tok.indexOf('=');
                    if(sep>0&&sep==tok.length()-1)continue; // explicit empty transaction marker, e.g. T8:
                    if(sep<=0)throw new IOException("Bad transaction token '"+tok+"' in: "+raw);
                    String rawItem=tok.substring(0,sep);String item=canonical.get(rawItem);
                    if(item==null)throw new IOException("No external utility for item "+rawItem);
                    int q=Integer.parseInt(tok.substring(sep+1));if(q<=0)continue;
                    if(n==items.length){items=Arrays.copyOf(items,n*2);qty=Arrays.copyOf(qty,n*2);}
                    items[n]=item;qty[n]=q;n++;
                }
                txs.add(new Transaction(tid++,items,qty,n));
            }
        }
        return new UtilityDatabase(txs,profits);
    }

    public static Set<ItemsetKey> readSensitive(Path path)throws IOException{
        LinkedHashSet<ItemsetKey> out=new LinkedHashSet<>();
        try(BufferedReader r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            String raw;while((raw=r.readLine())!=null){String line=strip(raw);if(!line.isBlank())out.add(ItemsetKey.parse(line));}
        }
        return out;
    }

    public static void writeSimple(UtilityDatabase db,Path path)throws IOException{
        Path parent=path.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        try(BufferedWriter w=new BufferedWriter(Files.newBufferedWriter(path,StandardCharsets.UTF_8),1<<20)){
            for(Transaction t:db.transactions()){
                if(t.isEmpty()){w.write("T"+t.tid()+":");w.newLine();continue;}
                boolean first=true;for(String item:t.activeItems()){
                    if(!first)w.write(' ');w.write(item);w.write(':');w.write(Integer.toString(t.quantity(item)));first=false;
                }w.newLine();
            }
        }
    }

    private static String strip(String s){int i=s.indexOf('#');return(i>=0?s.substring(0,i):s).trim();}
    private static String stripPreserveEmpty(String s){int i=s.indexOf('#');return i>=0?s.substring(0,i):s;}
}
