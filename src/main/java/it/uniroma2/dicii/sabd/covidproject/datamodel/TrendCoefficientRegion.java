package it.uniroma2.dicii.sabd.covidproject.datamodel;

import java.io.Serializable;

/* This class is used to convert the RDD containing (trendLine, region) pairs to a DataFrame
* in order to apply K-Means through Spark MLib library.
* */

public class TrendCoefficientRegion implements Serializable {
    private Double trendLineCoefficient;
    private String regionName;

    public TrendCoefficientRegion(Double trendLineCoefficient, String regionName) {
        this.trendLineCoefficient = trendLineCoefficient;
        this.regionName = regionName;
    }

    public TrendCoefficientRegion() {
    }

    public Double getTrendLineCoefficient() {
        return trendLineCoefficient;
    }

    public void setTrendLineCoefficient(Double trendLineCoefficient) {
        this.trendLineCoefficient = trendLineCoefficient;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }
}
