package it.uniroma2.dicii.sabd.covidproject;

/*
 * This query is based on a dataset on Covid-19 provided by the Center for Systems Science and Engineering at
 * John Hopkins University, USA. The dataset, in CSV format, reports the current number of confirmed cases of Covid in the world.
 * In detail, each row contains: a Country, optionally the corresponding Province/State, Latitude and Longitude and
 * the daily columns with total number of confirmed cases, starting from January 22, 2020. Note that available data are
 * cumulative. For further details, see:
 * https://github.com/CSSEGISandData/COVID-19/blob/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_global.csv#L11
 *
 * The aim of the query is to determine, for each continent, the average, standard deviation, minimum
 * and maximum number of daily confirmed cases on a weekly basis. For this purpose, only the top-100 affected Regions are
 * have to be considered and the identification of these regions can be made estimating the trendline coefficient.
 *
 * The query is answered using Apache Spark.
 */

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

public class Query2 {

    // TODO MOVE FUNCTION SINCE IT IS SHARED BY QUERY3
    /* Estimate the trend line coefficient used to identify the mostly affected regions.
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

    /* Parse a line of the CSV dataset */
    private static Tuple2<Double, RegionData> parseInputLine(String line) {

        /* Extract fields of interest from CSV line */
        String[] csvFields = line.split(",");
        /* If province/state is not available, the country is considered */
        String regionName = csvFields[0].equals("") ? csvFields[1] : csvFields[0];
        Double latitude = Double.parseDouble(csvFields[2]);
        Double longitude = Double.parseDouble(csvFields[3]);
        /* Retrieve number of days available for computations of daily increments of confirmed cases.
        *  The first day in the dataset is not considered because the corresponding increment cannot be computed.
        *  Furthermore, only completed week are considered */
        // TODO Remove duplicated code between Query2 and Query3
        int availableDays = ((csvFields.length - 5) - ((csvFields.length - 5) % 7));
        Double[] dailyCumulativeConfirmed = new Double[availableDays + 1];
        for (int i = 0; i < dailyCumulativeConfirmed.length; i++) {
            dailyCumulativeConfirmed[i] = Double.parseDouble(csvFields[i+4]);
        }
        /* Convert cumulative confirmed to confirmed daily increments */
        Double[] confirmedDailyIncrements = new Double[availableDays];

        for (int i = 0; i < availableDays; i++) {
            confirmedDailyIncrements[i] = dailyCumulativeConfirmed[i+1]-dailyCumulativeConfirmed[i];
        }
        /* Build RegionData object, representing the parsed line */
        RegionData regionData =  new RegionData(regionName, latitude, longitude, confirmedDailyIncrements);
        /* Estimate Trend Line Coefficient associated to the world region */
        Double tlcEstimate = computeCoefficientEstimate(confirmedDailyIncrements);
        return new Tuple2<>(tlcEstimate, regionData);
    }


    /*
    * For each one of the top-100 affected regions, the method generates a list of (k,v) pairs, where k is a combination
    * of the region continent and a week and v is the array of daily increments reported in that week.
    * */
    private static Iterator<Tuple2<String, Double[]>> mapDataToWeekAndContinent(Tuple2<Tuple2<Double, RegionData>,Long> regionData) {

        /* Continent identification is made using latitude and longitude through the utility class UtilsContinent */
        UtilsContinent utilsContinent = new UtilsContinent();
        UtilsContinent.Continent continent =  utilsContinent.getContinentFromLatLong(regionData._1._2.getLatitude(), regionData._1._2.getLongitude());
        List<Tuple2<String, Double[]>> output = new ArrayList<>();
        Double[] confirmedDailyIncrements = regionData._1._2.getConfirmedDailyIncrements();
        int numOfWeeks = confirmedDailyIncrements.length / 7;
        /* For each available week, an array of daily increments is produced and emitted along with the continent and the week index */
        for (int k = 0; k < numOfWeeks; k++) {
            Double[] weeklyIncrements = new Double[7];
            System.arraycopy(confirmedDailyIncrements, k * 7, weeklyIncrements, 0, 7);
            String key = continent.toString() + "," + k;
            output.add(new Tuple2<>(key, weeklyIncrements));
        }
        return output.iterator();
    }

    /*
    * Compute the statistics of interest for each combination of continents and weeks
    * */
    private static ContinentWeeklyStats computeContinentWeeklyStats(Double[] continentWeeklyIncrements) {

        ContinentWeeklyStats continentWeeklyStats = new ContinentWeeklyStats();
        double[] continentWeeklyIncrementsDouble = new double[continentWeeklyIncrements.length];
        for (int i = 0; i < continentWeeklyIncrementsDouble.length; i++) {
            continentWeeklyIncrementsDouble[i] = continentWeeklyIncrements[i];
        }
        /* Computations are made using Apache Commons Math library */
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
        /* Spark setup */
        SparkConf conf = new SparkConf().setAppName("Query-2");
        JavaSparkContext sc = new JavaSparkContext(conf);
        /* Import input file */
        JavaRDD<String> inputRDD = sc.textFile(args[0]);
        // TODO possibly modify in case of header and malformed input handling at ingestion-time
        JavaRDD<String> rddInputWithoutHeader = inputRDD.filter(line -> !line.contains("Province") && !line.contains("\""));
        /* Identify the top-100 affected regions */
        JavaPairRDD<Double, RegionData> regionsData = rddInputWithoutHeader.mapToPair(Query2::parseInputLine).sortByKey(false);
        JavaPairRDD<Tuple2<Double,RegionData>, Long> top100AffectedRegionData = regionsData.zipWithIndex().filter(x -> x._2 <= 100);
        /* Aggregate data per continent and week */
        JavaPairRDD<String, Double[]> continentWeeklyIncrements = top100AffectedRegionData
                .flatMapToPair(Query2::mapDataToWeekAndContinent)
                .reduceByKey((x,y) -> {
                    Double[] result = new Double[x.length];
                    Arrays.setAll(result, i -> x[i] + y[i]);
                    return result;
                });
        /* Compute the statistics of interest for each continent and week */
        JavaPairRDD<String, ContinentWeeklyStats> continentWeeklyStats = continentWeeklyIncrements.
                mapValues(Query2::computeContinentWeeklyStats).sortByKey(new KeyContinentWeekComparator());
        /* Save results in CSV format into the output directory provided by the user */
        JavaRDD<String> csvOutput = continentWeeklyStats.map(
                cws -> cws._1 + "," + cws._2.getAvgConfirmed() + "," + cws._2.getMaxConfirmed() + ","
                        + cws._2.getMinConfirmed() + "," + cws._2.getStdevConfirmed());
        csvOutput.saveAsTextFile(args[1]);
        /* Spark shutdown */
        sc.stop();
    }

}
