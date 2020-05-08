package it.uniroma2.dicii.sabd.covidproject;

import it.uniroma2.dicii.sabd.covidproject.datamodel.MonthlyRegionData;
import it.uniroma2.dicii.sabd.covidproject.utils.TrendLineMonthlyRegionComparator;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.util.*;

public class Query3 {

    private static Iterator<Tuple2<Integer, MonthlyRegionData>> parseInputLine (String line){

        // Extract fields of interest from CSV line
        String[] csvFields = line.split(",");
        String regionName = csvFields[0].equals("") ? csvFields[1] : csvFields[0];
        // Retrieve num of days available for daily increments computations
        // Only completed months are considered and the first day is not considered since the increment is not
        // computable. The months duration is assumed to be 30 days for sake of simplicity
        int availableDays = ((csvFields.length - 5) - ((csvFields.length - 5) % 30));
        Double[] dailyCumulativeConfirmed = new Double[availableDays + 1];
        for (int i = 0; i < dailyCumulativeConfirmed.length; i++) {
            dailyCumulativeConfirmed[i] = Double.parseDouble(csvFields[i+4]);
        }
        // Convert cumulative confirmed to confirmed daily increments
        Double[] confirmedDailyIncrements = new Double[availableDays];

        for (int i = 0; i < availableDays; i++) {
            confirmedDailyIncrements[i] = dailyCumulativeConfirmed[i+1]-dailyCumulativeConfirmed[i];
        }

        int numberOfMonths = confirmedDailyIncrements.length / 30;

        // Build MonthlRegionData object
        List<Tuple2<Integer, MonthlyRegionData>> output = new ArrayList<>();
        for (int i = 0; i < numberOfMonths; i++){
            Double[] monthlyIncrements = new Double[30];
            System.arraycopy(confirmedDailyIncrements, i * 30, monthlyIncrements, 0, 30);
            MonthlyRegionData monthlyRegionData = new MonthlyRegionData();
            monthlyRegionData.setName(regionName);
            monthlyRegionData.setTrendLineCoefficient(Query2.computeCoefficientEstimate(monthlyIncrements));
            output.add(new Tuple2<>(i, monthlyRegionData));
        }

        return output.iterator();
    }


    public static void main(String[] args) {

        if (args.length != 2) {
            System.err.println("Input file and output directory required");
            System.exit(1);
        }

        SparkConf conf = new SparkConf().setAppName("Query-3").setMaster("local");
        JavaSparkContext sc = new JavaSparkContext(conf);

        JavaRDD<String> inputRDD = sc.textFile(args[0]); // import input file
        // TODO header removal...
        // TODO ho rimosso record strani...
        JavaRDD<String> rddInputWithoutHeader = inputRDD.filter(line -> !line.contains("Province") && !line.contains("\""));
        JavaPairRDD<Integer, MonthlyRegionData> monthlyRegionsData =
                rddInputWithoutHeader.flatMapToPair(Query3::parseInputLine).cache();

        long numberOfMonths = monthlyRegionsData.groupByKey().count();

        //Apply K-Means to each month
        for (int k = 0; k < numberOfMonths; k++){
            final int monthIndex = k;
            JavaPairRDD<Integer, MonthlyRegionData> kthMonthRegionData =
                    monthlyRegionsData.filter(x -> x._1 == monthIndex);

            List<Tuple2<Double, String>> top50MonthlyAffectedRegion = kthMonthRegionData
                    .mapToPair(x -> new Tuple2<>(x._2.getTrendLineCoefficient(), x._2.getName()))
                    .top(50, new TrendLineMonthlyRegionComparator());

            JavaPairRDD<Double, String> top50MonthlyAffectedRegionRDD =
                    sc.parallelizePairs(top50MonthlyAffectedRegion).cache();

            // TODO DEBUG SAVE
            top50MonthlyAffectedRegionRDD.saveAsTextFile(args[1] + "/month" + monthIndex);
        }

        sc.stop();
    }

}
