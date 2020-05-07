package it.uniroma2.dicii.sabd.covidproject.datamodel;

/*
* This object represents Region data related to Covid-19 outbreak.
* */

import java.io.Serializable;
import java.util.Arrays;

public class RegionData implements Serializable {

    private String name;
    private Double latitude;
    private Double longitude;
    private Double[] confirmedDailyIncrements;

    public RegionData(String name, Double latitude, Double longitude, Double[] confirmedDailyIncrements) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.confirmedDailyIncrements = confirmedDailyIncrements;
    }



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

    @Override
    public String toString() {
        return "RegionData{" +
                "name='" + name + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", confirmedDailyIncrements=" + Arrays.toString(confirmedDailyIncrements) +
                '}';
    }
}
