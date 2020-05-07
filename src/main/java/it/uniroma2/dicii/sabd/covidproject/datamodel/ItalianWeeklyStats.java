package it.uniroma2.dicii.sabd.covidproject.datamodel;

import java.io.Serializable;

public class ItalianWeeklyStats implements Serializable {

    private Float cured;
    private Float swabs;

    public ItalianWeeklyStats() {
    }

    public ItalianWeeklyStats(Float cured, Float swabs) {
        this.cured = cured;
        this.swabs = swabs;
    }

    public Float getCured() {
        return cured;
    }

    public void setCured(Float cured) {
        this.cured = cured;
    }

    public Float getSwabs() {
        return swabs;
    }

    public void setSwabs(Float swabs) {
        this.swabs = swabs;
    }
}
