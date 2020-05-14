package it.uniroma2.dicii.sabd.covidproject.utils;

/*
* This class provides utility methods used in answering query 2 and 3, the ones related to the global dataset on
* Covid-19
* */

public class GlobalDataUtils {

    /*
     *  Estimate the trend line coefficient used to identify the mostly affected regions.
     *  The estimate is computed considering the slope of the regression line against the confirmed daily increments
     *  of a region.
     * */
    public static Double computeCoefficientEstimate(Double[] confirmedDailyIncrements) {

        Double avgIncrements;
        Double incrementsSum = 0D;
        int n = confirmedDailyIncrements.length;
        for (Double confirmedDailyIncrement : confirmedDailyIncrements) {
            incrementsSum += confirmedDailyIncrement;
        }
        avgIncrements = incrementsSum / n;
        double num = 0F;
        double den = 0F;
        for (int i = 1; i <= n; i++) {
            num += (i-(double)(n+1)/2)*(confirmedDailyIncrements[i-1]-avgIncrements);
            den += (i-(double)(n+1)/2)*(i-(double)(n+1)/2);
        }
        return num / den;

    }

    //TODO
    /*
    * Convert a vector of cumulative cases of Covid to a vector of daily increments.
    * Errors in original dataset may cause negative increments. Negative increments are truncated to 0
    * */
    public static Double[] convertCumulativeToIncrement(int availableDays, String[] csvFields) {
        Double[] dailyCumulativeConfirmed = new Double[availableDays + 1];
        for (int i = 0; i < dailyCumulativeConfirmed.length; i++) {
            dailyCumulativeConfirmed[i] = Double.parseDouble(csvFields[i+8]);
        }
        Double[] confirmedDailyIncrements = new Double[availableDays];
        for (int i = 0; i < availableDays; i++) {
            double dailyIncrements = dailyCumulativeConfirmed[i+1]-dailyCumulativeConfirmed[i];
            confirmedDailyIncrements[i] = (dailyIncrements < 0) ? 0 : dailyIncrements;
        }
        return confirmedDailyIncrements;
    }
}
