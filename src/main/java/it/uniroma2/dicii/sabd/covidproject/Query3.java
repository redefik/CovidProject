package it.uniroma2.dicii.sabd.covidproject;

import it.uniroma2.dicii.sabd.covidproject.datamodel.MonthlyRegionData;
import it.uniroma2.dicii.sabd.covidproject.datamodel.TrendCoefficientRegion;
import it.uniroma2.dicii.sabd.covidproject.utils.GlobalDataUtils;
import it.uniroma2.dicii.sabd.covidproject.utils.TrendLineMonthlyRegionComparator;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.ml.clustering.KMeans;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
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
        /* Convert cumulative data to daily increments of confirmed cases */
        Double[] confirmedDailyIncrements = GlobalDataUtils.convertCumulativeToIncrement(availableDays, csvFields);
        int numberOfMonths = confirmedDailyIncrements.length / 30;

        // Build MonthlRegionData object
        List<Tuple2<Integer, MonthlyRegionData>> output = new ArrayList<>();
        for (int i = 0; i < numberOfMonths; i++){
            Double[] monthlyIncrements = new Double[30];
            System.arraycopy(confirmedDailyIncrements, i * 30, monthlyIncrements, 0, 30);
            MonthlyRegionData monthlyRegionData = new MonthlyRegionData();
            monthlyRegionData.setName(regionName);
            monthlyRegionData.setTrendLineCoefficient(GlobalDataUtils.computeCoefficientEstimate(monthlyIncrements));
            output.add(new Tuple2<>(i, monthlyRegionData));
        }

        return output.iterator();
    }

    private static void kMeansMLib(JavaSparkContext sc, JavaPairRDD<Double,String> trendRegionPairs, String outputDirectory) {
        SparkSession ss = SparkSession.builder().config(sc.getConf()).getOrCreate();
        // Convert input RDD into DataFrame
        Dataset<Row> trendRegionsDF = ss.createDataFrame(trendRegionPairs.map(x -> new TrendCoefficientRegion(x._1, x._2)),
                TrendCoefficientRegion.class);
        // Set feature column in dataset
        String[] featureCols = {"trendLineCoefficient"};
        VectorAssembler assembler = new VectorAssembler().setInputCols(featureCols).setOutputCol("features");
        Dataset<Row> featuredDF = assembler.transform(trendRegionsDF);
        // Apply K-Means
        // TODO ? make the K configurable ?
        KMeans kMeans = new KMeans().setK(4).setSeed(1L);
        KMeansModel model = kMeans.fit(featuredDF);
        Dataset<Row> clusteredRegions = model.transform(featuredDF);
        // Save clustering results in a CSV file
        clusteredRegions.select("regionName","prediction")
                .write().format("csv").option("header", "false").save(outputDirectory);
    }


    public static void main(String[] args) {

        if (args.length != 3) {
            System.err.println("Input file, output directory and k-means mode required");
            System.exit(1);
        }

        String mode = args[2];
        if (!mode.equals("naive") && !(mode.equals("mlib"))) {
            System.err.println("Only \"naive\" and \"mlib\" mode are available");
            System.exit(2);
        }

        SparkConf conf = new SparkConf().setAppName("Query-3");
        JavaSparkContext sc = new JavaSparkContext(conf);

        JavaRDD<String> inputRDD = sc.textFile(args[0]); // import input file
        // TODO header removal...
        // TODO ho rimosso record strani...
        JavaRDD<String> rddInputWithoutHeader = inputRDD.filter(line -> !line.contains("Province") && !line.contains("\""));
        JavaPairRDD<Integer, MonthlyRegionData> monthlyRegionsData =
                rddInputWithoutHeader.flatMapToPair(Query3::parseInputLine).cache();

        long numberOfMonths = monthlyRegionsData.groupByKey().count();

        // Apply k-means algorithm to cluster regions with similar behaviour of confirmed cases during a month
        for (int k = 0; k < numberOfMonths; k++){
            final int monthIndex = k;
            JavaPairRDD<Integer, MonthlyRegionData> kthMonthRegionData =
                    monthlyRegionsData.filter(x -> x._1 == monthIndex);

            List<Tuple2<Double, String>> top50MonthlyAffectedRegion = kthMonthRegionData
                    .mapToPair(x -> new Tuple2<>(x._2.getTrendLineCoefficient(), x._2.getName()))
                    .top(50, new TrendLineMonthlyRegionComparator());

            JavaPairRDD<Double, String> top50MonthlyAffectedRegionRDD =
                    sc.parallelizePairs(top50MonthlyAffectedRegion).cache();

            if (mode.equals("naive")) {
                System.out.println("To be implemented");
                // TODO invoke naive implementation of k-means
            } else {
                kMeansMLib(sc, top50MonthlyAffectedRegionRDD, args[1] + "/month" + monthIndex);
            }


        }

        sc.stop();
    }

}
