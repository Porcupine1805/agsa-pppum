package org.ppum.agsa.sanitize;

public final class FastAgsaSanitizer extends AgsaSanitizer {
    public FastAgsaSanitizer(int topK){this(topK,AgsaWeights.defaults(),true,"FAGSA");}
    public FastAgsaSanitizer(int topK,double lm,double lu,double le,boolean guard){this(topK,new AgsaWeights(1000,60,lm,lu,le),guard,guard?"FAGSA":"FAGSA-NOGUARD");}
    public FastAgsaSanitizer(int topK,AgsaWeights w,boolean guard,String label){super(w,Math.max(1,topK),guard,label);}
}
