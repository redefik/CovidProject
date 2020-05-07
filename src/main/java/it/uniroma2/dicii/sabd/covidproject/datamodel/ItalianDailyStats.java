package it.uniroma2.dicii.sabd.covidproject.datamodel;

/*
* This object represents the daily cumulative statistics of interest related to Covid-19 outbreak in Italy
* */

public class ItalianDailyStats {

    private Integer dayOfWeek;
    private Integer weekOfYear;
    private Integer cumulativeCured;
    private Integer cumulativeSwabs;

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public void setWeekOfYear(Integer weekOfYear) {
        this.weekOfYear = weekOfYear;
    }

    public void setCumulativeCured(Integer cumulativeCured) {
        this.cumulativeCured = cumulativeCured;
    }

    public void setCumulativeSwabs(Integer cumulativeSwabs) {
        this.cumulativeSwabs = cumulativeSwabs;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public Integer getWeekOfYear() {
        return weekOfYear;
    }

    public Integer getCumulativeCured() {
        return cumulativeCured;
    }

    public Integer getCumulativeSwabs() {
        return cumulativeSwabs;
    }

}
