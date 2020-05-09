package it.uniroma2.dicii.sabd.covidproject.datamodel;

/*
* This class encapsulates the measurement of interests extracted from a line of the CSV input file used in the
* first query.
* */

public class ItalianDailyStats {

    private Integer dayOfWeek;
    private Integer weekOfYear;
    private Integer cumulativeCured; /* Number of currently cured people */
    private Integer cumulativeSwabs; /* Number of currently swab tests */

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
