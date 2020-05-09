package it.uniroma2.dicii.sabd.covidproject.datamodel;

/*
 * This class encapsulates the number of cured people and swabs test registered in a week in Italy,
 * either cumulative or absolute
 * */

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
