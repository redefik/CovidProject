package it.uniroma2.dicii.sabd.covidproject.datamodel;

import java.io.Serializable;

public class MonthlyRegionData implements Serializable {

    private String name;
    private Double trendLineCoefficient;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getTrendLineCoefficient() {
        return trendLineCoefficient;
    }

    public void setTrendLineCoefficient(Double trendLineCoefficient) {
        this.trendLineCoefficient = trendLineCoefficient;
    }
}
