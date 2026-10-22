package org.ppum.agsa.sanitize;

public record Edit(int iteration, int tid, String item, int oldQuantity, int newQuantity,
                   String targetSensitiveItemset, double score, String reason) {
    public int quantityReduction() { return oldQuantity - newQuantity; }
}
