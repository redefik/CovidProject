package it.uniroma2.dicii.sabd.covidproject;

import it.uniroma2.dicii.sabd.covidproject.model.ItalianDailyStats;
import it.uniroma2.dicii.sabd.covidproject.model.ItalianWeeklyStats;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

public class Query1 {

    private final static String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    private static ItalianDailyStats parseInputLine(String line) {

        // Extract fields of interest from CSV line
        String[] csvFields = line.split(",");
        String date = csvFields[0];
        Integer cumulativeCured = Integer.parseInt(csvFields[9]);
        Integer cumulativeSwabs = Integer.parseInt(csvFields[12]);
        // Parsing date field
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        LocalDate formattedDate = LocalDate.parse(date, formatter);
        // Build ItalianDailyStats object
        ItalianDailyStats italianDailyStats = new ItalianDailyStats();
        italianDailyStats.setCumulativeCured(cumulativeCured);
        italianDailyStats.setCumulativeSwabs(cumulativeSwabs);
        italianDailyStats.setDayOfWeek(formattedDate.getDayOfWeek().getValue());
        italianDailyStats.setWeekOfYear(formattedDate.get(WeekFields.ISO.weekOfWeekBasedYear()));

        return italianDailyStats;
    }

    private static Iterator<Tuple2<Integer, ItalianWeeklyStats>>  duplicateWeeklyStats(ItalianDailyStats italianDailyStats) {
        List<Tuple2<Integer, ItalianWeeklyStats>> pairs = new ArrayList<>();
        Integer weekIndex = italianDailyStats.getWeekOfYear();
        Integer cumulativeCured = italianDailyStats.getCumulativeCured();
        Integer cumulativeSwabs = italianDailyStats.getCumulativeSwabs();
        pairs.add(new Tuple2<>(weekIndex, new ItalianWeeklyStats((float)cumulativeCured, (float)cumulativeSwabs)));
        pairs.add(new Tuple2<>(weekIndex + 1, new ItalianWeeklyStats((float)cumulativeCured, (float)cumulativeSwabs)));
        return pairs.iterator();
    }

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

        if (args.length != 1) {
            System.err.println("Input file required");
            System.exit(1);
        }

        SparkConf conf = new SparkConf().setAppName("Query-1");
        JavaSparkContext sc = new JavaSparkContext(conf);

        JavaRDD<String> rddInput = sc.textFile(args[0]); // import input file
        JavaRDD<String> rddInputWithoutHeader = rddInput.filter(line -> !line.contains("data"));  // TODO ? remove header in data ingestion ?
        JavaRDD<ItalianDailyStats> italianDailyStats = rddInputWithoutHeader.map(Query1::parseInputLine);
        // TODO coalesce in filtering ?
        JavaRDD<ItalianDailyStats> italianWeeklyStats = italianDailyStats.filter(ids -> ids.getDayOfWeek() == 7);
        /* To obtain the absolute statistics the tuple are duplicated in the following way:
         Given an object with index of week x and stats y the tuples (x,y) and (x+1,y) are emitted */
        JavaPairRDD<Integer, ItalianWeeklyStats> duplicatedWeeklyStats = italianWeeklyStats.flatMapToPair(Query1::duplicateWeeklyStats);
        // Convert cumulative statistics to absolute statistics by subtraction
        JavaPairRDD<Integer, ItalianWeeklyStats> cachedWeeklyAbsoluteStats = duplicatedWeeklyStats.groupByKey()
                .mapValues(Query1::computeAbsoluteStats)
                .sortByKey(false)
                .cache();
        Tuple2<Integer, ItalianWeeklyStats> spuriousTuple = cachedWeeklyAbsoluteStats.first();
        JavaPairRDD<Integer, ItalianWeeklyStats> weeklyAbsoluteStats = cachedWeeklyAbsoluteStats.filter(t -> !t._1.equals(spuriousTuple._1));

        // TODO debug print
        Map<Integer, ItalianWeeklyStats> map = weeklyAbsoluteStats.collectAsMap();
        for (Integer w : map.keySet()) {
            System.out.println("week: " + w + " cured: " + map.get(w).getCured() + " swabs: " + map.get(w).getSwabs());
        }

        sc.stop();


    }

}
