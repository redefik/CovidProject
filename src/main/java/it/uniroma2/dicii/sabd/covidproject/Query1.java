package it.uniroma2.dicii.sabd.covidproject;

/*
 * This query is based on a dataset on Covid-19 provided by the Italian Civil Protection Department.
 * The dataset is in CSV format and contains one row per day, starting from February 23, 2020.
 * Each row provides many information such as: date, patients in hospitals with symptoms, total number
 * of current confirmed cases, daily new confirmed cases, number of swab tests... Most data are cumulative.
 * For further details, see the dataset available at:
 * https://github.com/pcm-dpc/COVID-19/blob/master/dati-andamento-nazionale/dpc-covid19-ita-andamento-nazionale.csv
 *
 * The aim of the query is to determine for each week the average number of cured people in a day and
 * the average number of swab tests in a day.
 *
 * The query is answered using Apache Spark.
 *
 * NOTE: In our data pipeline, the dataset described above has been pre-processed in order to remove the CSV header
 * and to extract only the fields of interest.
 * */

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import it.uniroma2.dicii.sabd.covidproject.datamodel.ItalianDailyStats;
import it.uniroma2.dicii.sabd.covidproject.datamodel.ItalianWeeklyStats;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

public class Query1 {

    private final static String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    /* Parse a line of the CSV dataset */
    private static ItalianDailyStats parseInputLine(String line) {

        try {
            /* Extract fields of interest from the CSV line */
            CSVReader csvReader = new CSVReader(new StringReader(line));
            String[] csvFields = csvReader.readNext();
            csvReader.close();
            if (csvFields == null) {
                return null;
            }
            String date = csvFields[0];
            Integer cumulativeCured = Integer.parseInt(csvFields[1]);
            Integer cumulativeSwabs = Integer.parseInt(csvFields[2]);
            /* Parsing date field */
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
            LocalDate formattedDate = LocalDate.parse(date, formatter);
            /* Build ItalianDailyStats object */
            ItalianDailyStats italianDailyStats = new ItalianDailyStats();
            italianDailyStats.setCumulativeCured(cumulativeCured);
            italianDailyStats.setCumulativeSwabs(cumulativeSwabs);
            italianDailyStats.setDayOfWeek(formattedDate.getDayOfWeek().getValue());
            italianDailyStats.setWeekOfYear(formattedDate.get(WeekFields.ISO.weekOfWeekBasedYear()));

            return italianDailyStats;
        } catch (CsvValidationException | IOException e) {
            return null;
        }
    }


    /* Utility method that performs the first phase of absolute statistics computation from cumulative measurements
    *  (see main() below) */
    private static Iterator<Tuple2<Integer, ItalianWeeklyStats>>  duplicateWeeklyStats(ItalianDailyStats italianDailyStats) {

        List<Tuple2<Integer, ItalianWeeklyStats>> pairs = new ArrayList<>();
        Integer weekIndex = italianDailyStats.getWeekOfYear();
        Integer cumulativeCured = italianDailyStats.getCumulativeCured();
        Integer cumulativeSwabs = italianDailyStats.getCumulativeSwabs();
        pairs.add(new Tuple2<>(weekIndex, new ItalianWeeklyStats((float)cumulativeCured, (float)cumulativeSwabs)));
        pairs.add(new Tuple2<>(weekIndex + 1, new ItalianWeeklyStats((float)cumulativeCured, (float)cumulativeSwabs)));
        return pairs.iterator();
    }

    /* Utility method that performs the second phase of absolute statistics computation from cumulative measurements
    *  (see main() below)*/
    private static ItalianWeeklyStats computeAbsoluteStats(Iterable<ItalianWeeklyStats> values) {

        ItalianWeeklyStats absoluteWeeklyStats = new ItalianWeeklyStats();
        List<ItalianWeeklyStats> valuesList = new ArrayList<>();
        for (ItalianWeeklyStats value : values) {
            valuesList.add(value);
        }
        Float cured1 = valuesList.get(0).getCured();
        Float swabs1 = valuesList.get(0).getSwabs();
        Float cured2;
        Float swabs2;
        if (valuesList.size() == 1) {
            cured2 = 0F;
            swabs2 = 0F;
        } else {
            cured2 = valuesList.get(1).getCured();
            swabs2 = valuesList.get(1).getSwabs();
        }
        absoluteWeeklyStats.setCured(Math.abs(cured1 - cured2) / 7);
        absoluteWeeklyStats.setSwabs(Math.abs(swabs1 - swabs2) / 7);
        return absoluteWeeklyStats;
    }

    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Input file and output directory required");
            System.exit(1);
        }
        /* Spark setup */
        SparkConf conf = new SparkConf().setAppName("Query-1");
        JavaSparkContext sc = new JavaSparkContext(conf);
        /* Import input CSV file */
        JavaRDD<String> rddInput = sc.textFile(args[0]);
        /* Parse input CSV file */
        JavaRDD<ItalianDailyStats> italianDailyStats = rddInput.map(Query1::parseInputLine).filter(Objects::nonNull);
        // TODO coalesce in filtering to improve performance ?
        /* Since the statistics are computed on a weekly basis, only the measurements made at the end of the weeks are preserved */
        JavaRDD<ItalianDailyStats> italianWeeklyStats = italianDailyStats.filter(ids -> ids.getDayOfWeek() == 7);
        /* Available data are cumulative but the required statistics are absolute. Then statistics computation is
        *  articulated in two phases:
        *  1. From each ItalianDailyStats object two <Integer,ItalianWeeklyStats> pairs are created.
        *  Both values of pairs encapsulate the cumulative number of cured people and swabs tests
        *  referring to a week, say i, associated to the ItalianDailyStats objects. Instead, the first pair has key i and
        *  the second one key i+1.
        *  2. In this way, the average daily increments can be computed grouping pairs with the
        *  same key and dividing by 7 the absolute value of the difference of the cumulative values */
        JavaPairRDD<Integer, ItalianWeeklyStats> weeklyAbsoluteStats = italianWeeklyStats
                .flatMapToPair(Query1::duplicateWeeklyStats)
                .groupByKey()
                .mapValues(Query1::computeAbsoluteStats)
                .sortByKey()
                .cache();
        /* The previous conversion produces a spurious element, that here is removed */
        long numOfWeeks = weeklyAbsoluteStats.count();
        JavaPairRDD<Tuple2<Integer, ItalianWeeklyStats>, Long> weeklyAbsoluteStatsCleaned = weeklyAbsoluteStats
                .zipWithIndex()
                .filter(x -> x._2 != numOfWeeks - 1);
        /* Save results as a CSV file in the output directory provided by the user */
        JavaRDD<String> csvOutput = weeklyAbsoluteStatsCleaned.map(was -> "" + was._1._1 + "," + was._1._2.getCured() + "," + was._1._2.getSwabs());
        csvOutput.saveAsTextFile(args[1]);
        /* Spark shutdown */
        sc.stop();
    }

}
