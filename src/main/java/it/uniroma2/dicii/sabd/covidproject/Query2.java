package it.uniroma2.dicii.sabd.covidproject;

import it.uniroma2.dicii.sabd.covidproject.datamodel.ContinentWeeklyStats;
import it.uniroma2.dicii.sabd.covidproject.datamodel.RegionData;
import it.uniroma2.dicii.sabd.covidproject.utils.KeyContinentWeekComparator;
import it.uniroma2.dicii.sabd.covidproject.utils.UtilsContinent;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.util.*;

/*
* Dataset: https://github.com/CSSEGISandData/COVID-19/blob/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_global.csv#L11
* For each continent determine the average, standard deviation, minimum and maximum number
* of confirmed cases on a weekly basis considering only the top-100 affected states.
* The trendline coefficient is used to identify the top-100 affected states.
*/

public class Query2 {

    //TODO COMMON TO QUERY3

    // Estimating trendline coefficient
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
        // The estimate is computed considering the slope of the regression line against the confirmed daily increments
        for (int i = 1; i <= n; i++) {
            num += (i-(double)(n+1)/2)*(confirmedDailyIncrements[i-1]-avgIncrements);
            den += (i-(double)(n+1)/2)*(i-(double)(n+1)/2);
        }
        return num / den;

    }

    private static Tuple2<Double, RegionData> parseInputLine(String line) {

        // Extract fields of interest from CSV line
        String[] csvFields = line.split(",");
        String regionName = csvFields[0].equals("") ? csvFields[1] : csvFields[0];
        Double latitude = Double.parseDouble(csvFields[2]);
        Double longitude = Double.parseDouble(csvFields[3]);
        // Retrieve num of days available for daily increments computations
        // Only completed week are considered and the first day is not considered since the increment is not
        // computable
        int availableDays = ((csvFields.length - 5) - ((csvFields.length - 5) % 7));
        Double[] dailyCumulativeConfirmed = new Double[availableDays + 1];
        for (int i = 0; i < dailyCumulativeConfirmed.length; i++) {
            dailyCumulativeConfirmed[i] = Double.parseDouble(csvFields[i+4]);
        }
        // Convert cumulative confirmed to confirmed daily increments
        Double[] confirmedDailyIncrements = new Double[availableDays];

        for (int i = 0; i < availableDays; i++) {
            confirmedDailyIncrements[i] = dailyCumulativeConfirmed[i+1]-dailyCumulativeConfirmed[i];
        }
        // Build RegionData object
        RegionData regionData =  new RegionData(regionName, latitude, longitude, confirmedDailyIncrements);
        // Estimate Trend Line Coefficient
        Double tlcEstimate = computeCoefficientEstimate(confirmedDailyIncrements);
        return new Tuple2<>(tlcEstimate, regionData);
    }

    /* This method creates a list of <k,v> from each RegionData, where k is a combination of continent and week
       and v is the array representing the daily increments along the available weeks.*/
    private static Iterator<Tuple2<String, Double[]>> mapDataToWeekAndContinent(Tuple2<Tuple2<Double, RegionData>,Long> regionData) {

        UtilsContinent utilsContinent = new UtilsContinent();
        // An utility class is used to translate the <latitude, longitude> pair in the corresponding continent
        UtilsContinent.Continent continent =  utilsContinent.getContinentFromLatLong(regionData._1._2.getLatitude(), regionData._1._2.getLongitude());
        List<Tuple2<String, Double[]>> output = new ArrayList<>();
        Double[] confirmedDailyIncrements = regionData._1._2.getConfirmedDailyIncrements();
        int numOfWeeks = confirmedDailyIncrements.length / 7;
        // Split daily increments array in several increments array on a weekly basis
        for (int k = 0; k < numOfWeeks; k++) {
            Double[] weeklyIncrements = new Double[7];
            System.arraycopy(confirmedDailyIncrements, k * 7, weeklyIncrements, 0, 7);
            String key = continent.toString() + "," + k;
            output.add(new Tuple2<>(key, weeklyIncrements));
        }
        return output.iterator();
    }

    /* For each week and continent average daily increments, minimum daily increment, maximum daily increment,
     standard deviation of daily increments are computed */
    private static ContinentWeeklyStats computeContinentWeeklyStats(Double[] continentWeeklyIncrements) {

        ContinentWeeklyStats continentWeeklyStats = new ContinentWeeklyStats();
        double[] continentWeeklyIncrementsDouble = new double[continentWeeklyIncrements.length];
        for (int i = 0; i < continentWeeklyIncrementsDouble.length; i++) {
            continentWeeklyIncrementsDouble[i] = continentWeeklyIncrements[i];
        }
        // Statistics computation is based on Apache Commons Math library
        continentWeeklyStats.setMinConfirmed(StatUtils.min(continentWeeklyIncrementsDouble));
        continentWeeklyStats.setMaxConfirmed(StatUtils.max(continentWeeklyIncrementsDouble));
        continentWeeklyStats.setAvgConfirmed(StatUtils.mean(continentWeeklyIncrementsDouble));
        StandardDeviation standardDeviation = new StandardDeviation();
        continentWeeklyStats.setStdevConfirmed(standardDeviation.evaluate(continentWeeklyIncrementsDouble));
        return continentWeeklyStats;
    }

    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Input file and output directory required");
            System.exit(1);
        }

        SparkConf conf = new SparkConf().setAppName("Query-2");
        JavaSparkContext sc = new JavaSparkContext(conf);

        JavaRDD<String> inputRDD = sc.textFile(args[0]); // import input file
        // TODO header removal...
        // TODO ho rimosso record strani...
        JavaRDD<String> rddInputWithoutHeader = inputRDD.filter(line -> !line.contains("Province") && !line.contains("\""));
        // Select the top-100 affected Regions
        JavaPairRDD<Double, RegionData> regionsData = rddInputWithoutHeader.mapToPair(Query2::parseInputLine).sortByKey(false);
        JavaPairRDD<Tuple2<Double,RegionData>, Long> topKAffectedRegionData = regionsData.zipWithIndex().filter(x -> x._2 <= 100);
        // Aggregate data per continent and week
        JavaPairRDD<String, Double[]> continentWeeklyIncrements = topKAffectedRegionData.flatMapToPair(Query2::mapDataToWeekAndContinent)
                .reduceByKey((x,y) -> {
                    Double[] result = new Double[x.length];
                    Arrays.setAll(result, i -> x[i] + y[i]);
                    return result;
                });
        // Compute aggregate statistics per continent and week
        JavaPairRDD<String, ContinentWeeklyStats> continentWeeklyStats = continentWeeklyIncrements.
                mapValues(Query2::computeContinentWeeklyStats).sortByKey(new KeyContinentWeekComparator());
        // Save results in CSV format
        JavaRDD<String> csvOutput = continentWeeklyStats.map(
                cws -> cws._1 + "," + cws._2.getAvgConfirmed() + "," + cws._2.getMaxConfirmed() + ","
                        + cws._2.getMinConfirmed() + "," + cws._2.getStdevConfirmed());
        csvOutput.saveAsTextFile(args[1]);
        sc.stop();
    }

}
