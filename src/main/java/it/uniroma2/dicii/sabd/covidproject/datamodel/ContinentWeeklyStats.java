package it.uniroma2.dicii.sabd.covidproject.datamodel;

import java.io.Serializable;

/*
* This object encapsulates the statistics related to the confirmed cases of Covid for a continent during a specific
* week.
* */

public class ContinentWeeklyStats implements Serializable {

    private Double avgConfirmed;
    private Double stdevConfirmed;
    private Double minConfirmed;
    private Double maxConfirmed;

    public Double getAvgConfirmed() {
        return avgConfirmed;
    }

    public void setAvgConfirmed(Double avgConfirmed) {
        this.avgConfirmed = avgConfirmed;
    }

    public Double getStdevConfirmed() {
        return stdevConfirmed;
    }

    public void setStdevConfirmed(Double stdevConfirmed) {
        this.stdevConfirmed = stdevConfirmed;
    }

    public Double getMinConfirmed() {
        return minConfirmed;
    }

    public void setMinConfirmed(Double minConfirmed) {
        this.minConfirmed = minConfirmed;
    }

    public Double getMaxConfirmed() {
        return maxConfirmed;
    }

    public void setMaxConfirmed(Double maxConfirmed) {
        this.maxConfirmed = maxConfirmed;
    }

    @Override
    public String toString() {
        return "ContinentWeeklyStats{" +
                "avgConfirmed=" + avgConfirmed +
                ", stdevConfirmed=" + stdevConfirmed +
                ", minConfirmed=" + minConfirmed +
                ", maxConfirmed=" + maxConfirmed +
                '}';
    }
}
