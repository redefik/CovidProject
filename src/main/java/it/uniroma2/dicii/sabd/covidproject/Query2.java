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

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import it.uniroma2.dicii.sabd.covidproject.datamodel.ContinentWeeklyStats;
import it.uniroma2.dicii.sabd.covidproject.datamodel.RegionData;
import it.uniroma2.dicii.sabd.covidproject.utils.GlobalDataUtils;
import it.uniroma2.dicii.sabd.covidproject.utils.KeyContinentWeekComparator;
import it.uniroma2.dicii.sabd.covidproject.utils.UtilsContinent;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class Query2 {

    /* Parse a line of the CSV dataset */
    private static Tuple2<Double, RegionData> parseInputLine(String line) {

        try {
            /* Extract fields of interest from CSV line */
            CSVReader csvReader = new CSVReader(new StringReader(line));
            String[] csvFields = csvReader.readNext();
            csvReader.close();
            /* If province/state is not available, the country is considered */
            String regionName = csvFields[0].equals("") ? csvFields[1] : csvFields[0];
            Double latitude = Double.parseDouble(csvFields[2]);
            Double longitude = Double.parseDouble(csvFields[3]);
            /* Retrieve number of days available for computations of daily increments of confirmed cases.
             *  The first day in the dataset is not considered because the corresponding increment cannot be computed.
             *  Furthermore, only completed week are considered */
            int availableDays = ((csvFields.length - 5) - ((csvFields.length - 5) % 7));
            /* Convert cumulative data to daily increments */
            Double[] confirmedDailyIncrements = GlobalDataUtils.convertCumulativeToIncrement(availableDays, csvFields);
            /* Build RegionData object, representing the parsed line */
            RegionData regionData = new RegionData(regionName, latitude, longitude, confirmedDailyIncrements);
            /* Estimate Trend Line Coefficient associated to the world region */
            Double tlcEstimate = GlobalDataUtils.computeCoefficientEstimate(confirmedDailyIncrements);
            return new Tuple2<>(tlcEstimate, regionData);
        } catch (IOException | CsvValidationException e) {
            return new Tuple2<>(null, null); /* null in case of malformed input */
        }

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
        // TODO possibly modify in case of header handling at ingestion-time
        JavaRDD<String> rddInputWithoutHeader = inputRDD.filter(line -> !line.contains("Province"));
        /* Identify the top-100 affected regions */
        JavaPairRDD<Double, RegionData> regionsData = rddInputWithoutHeader.mapToPair(Query2::parseInputLine)
                .filter(x -> x._1 != null).sortByKey(false);
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
