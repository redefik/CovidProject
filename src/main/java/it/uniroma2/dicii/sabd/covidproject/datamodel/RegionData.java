package it.uniroma2.dicii.sabd.covidproject.datamodel;

/*
 * This class represents the covid-related data associated to a world region.
 * */

import java.io.Serializable;

public class RegionData implements Serializable {

    private String name;                       /* country/province name */
    private Double latitude;
    private Double longitude;
    private Double[] confirmedDailyIncrements; /* daily increments of confirmed cases of Covid */
                                               /* NOTE: the length of this array depends on the considered period of observation (week, month...) */
    private Double trendLineCoefficient;       /* trend line coefficient that approximates the trend corresponding to confirmedDailyIncrements */
    private Integer month;                     /* if populated, it represents the index of the month corresponding to the above statistics */

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double[] getConfirmedDailyIncrements() {
        return confirmedDailyIncrements;
    }

    public void setConfirmedDailyIncrements(Double[] confirmedDailyIncrements) {
        this.confirmedDailyIncrements = confirmedDailyIncrements;
    }

    public Double getTrendLineCoefficient() {
        return trendLineCoefficient;
    }

    public void setTrendLineCoefficient(Double trendLineCoefficient) {
        this.trendLineCoefficient = trendLineCoefficient;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

}
