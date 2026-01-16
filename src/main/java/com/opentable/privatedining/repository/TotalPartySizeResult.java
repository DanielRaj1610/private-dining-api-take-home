package com.opentable.privatedining.repository;

/**
 * Aggregation result for sum of party sizes.
 */
public class TotalPartySizeResult {
    private Integer totalPartySize;

    public TotalPartySizeResult() {}

    public Integer getTotalPartySize() {
        return totalPartySize;
    }

    public void setTotalPartySize(Integer totalPartySize) {
        this.totalPartySize = totalPartySize;
    }
}
