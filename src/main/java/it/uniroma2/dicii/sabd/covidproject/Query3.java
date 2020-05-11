package it.uniroma2.dicii.sabd.covidproject;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import it.uniroma2.dicii.sabd.covidproject.datamodel.MonthlyRegionData;
import it.uniroma2.dicii.sabd.covidproject.datamodel.TrendCoefficientRegion;
import it.uniroma2.dicii.sabd.covidproject.utils.GlobalDataUtils;
import it.uniroma2.dicii.sabd.covidproject.utils.TrendLineMonthlyRegionComparator;
import org.apache.commons.math3.stat.StatUtils;
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

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class Query3 {

    private static Iterator<Tuple2<Integer, MonthlyRegionData>> parseInputLine (String line){

        try {
            // Extract fields of interest from CSV line
            CSVReader csvReader = new CSVReader(new StringReader(line));
            String[] csvFields = csvReader.readNext();
            csvReader.close();
            String regionName = csvFields[0].equals("") ? csvFields[1] : csvFields[0];
            // Retrieve num of days available for daily increments computations
            // Only completed months are considered and the first day is not considered since the increment is not
            // computable. The months duration is assumed to be 30 days for sake of simplicity
            int availableDays = ((csvFields.length - 5) - ((csvFields.length - 5) % 30));
            /* Convert cumulative data to daily increments of confirmed cases */
            Double[] confirmedDailyIncrements = GlobalDataUtils.convertCumulativeToIncrement(availableDays, csvFields);
            int numberOfMonths = confirmedDailyIncrements.length / 30;

            // Build MonthlyRegionData object
            List<Tuple2<Integer, MonthlyRegionData>> output = new ArrayList<>();
            for (int i = 0; i < numberOfMonths; i++) {
                Double[] monthlyIncrements = new Double[30];
                System.arraycopy(confirmedDailyIncrements, i * 30, monthlyIncrements, 0, 30);
                MonthlyRegionData monthlyRegionData = new MonthlyRegionData();
                monthlyRegionData.setName(regionName);
                monthlyRegionData.setTrendLineCoefficient(GlobalDataUtils.computeCoefficientEstimate(monthlyIncrements));
                output.add(new Tuple2<>(i, monthlyRegionData));
            }

            return output.iterator();
        } catch (CsvValidationException | IOException e) {
            List<Tuple2<Integer, MonthlyRegionData>> failedOutput = new ArrayList<>();
            failedOutput.add(new Tuple2<>(null, null));
            return failedOutput.iterator();
        }
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
        clusteredRegions.select("prediction","regionName","trendLineCoefficient")
                .write().format("csv").option("header", "false").save(outputDirectory);
    }

    public static Tuple2<Integer, TrendCoefficientRegion> assignRegionToCluster(Tuple2<Double,String> trendRegion, Double[] centroids) {
        int minDistanceCentroid = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < centroids.length; i++) {
            double distance = Math.abs(centroids[i] - trendRegion._1);
            if (distance < minDistance) {
                minDistance = distance;
                minDistanceCentroid = i;
            }
        }
        return new Tuple2<>(minDistanceCentroid, new TrendCoefficientRegion(trendRegion._1, trendRegion._2));
    }

    public static Double computeClusterCentroid(Iterable<TrendCoefficientRegion> clusterPoints) {
        double sum = 0.0;
        int count = 0;
        for (TrendCoefficientRegion tcr : clusterPoints) {
            sum += tcr.getTrendLineCoefficient();
            count += 1;
        }
        return  sum / count;
    }

    public static Double computeEuclideanDistance(Double[] a1, Double[] a2) {
      double sum = 0.0;
      for (int i = 0; i < a1.length; i++) {
          sum += (a1[i]-a2[i])*(a1[i]-a2[i]);
      }
      return Math.sqrt(sum);
    }

    public static Double[] fromDoubleListToDoubleArray(List<Double> list) {
        Double[] array = new Double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    // TODO review caching
    public static void kMeansNaive(JavaPairRDD<Double,String> trendRegionPairs, String outputDirectory) {
        // TODO ? make K,MAX_ITER,PRECISION,SEED configurable ?
        List<Double> trendCoefficients = trendRegionPairs.map(x -> x._1).distinct().collect();
        double[] trendCoefficientsArray = new double[trendCoefficients.size()];
        for (int i = 0; i < trendCoefficients.size(); i++) {
            trendCoefficientsArray[i] = trendCoefficients.get(i);
        }
        double firstCentroid = StatUtils.percentile(trendCoefficientsArray, (float)1/8 * 100);
        System.out.println(firstCentroid);
        double secondCentroid = StatUtils.percentile(trendCoefficientsArray, (float)3/8 * 100);
        System.out.println(secondCentroid);
        double thirdCentroid = StatUtils.percentile(trendCoefficientsArray, (float)5/8 * 100);
        System.out.println(thirdCentroid);
        double fourthCentroid = StatUtils.percentile(trendCoefficientsArray, (float)7/8 * 100);
        System.out.println(fourthCentroid);
        Double[] newCentroids = {firstCentroid, secondCentroid, thirdCentroid, fourthCentroid};
        JavaPairRDD<Integer, TrendCoefficientRegion> clusteredTrendCoefficientRegions = null;
        for (int i = 0; i < 20; i++) {
            Double[] centroids = newCentroids;
            clusteredTrendCoefficientRegions = trendRegionPairs.mapToPair(x -> assignRegionToCluster(x, centroids));
            clusteredTrendCoefficientRegions.foreach(x -> {
                System.out.println(x._1 + "," + x._2.getRegionName());
            });
            JavaPairRDD<Integer, Iterable<TrendCoefficientRegion>> trendRegionsByCluster = clusteredTrendCoefficientRegions.groupByKey();
            // TODO caching?
            newCentroids = fromDoubleListToDoubleArray(trendRegionsByCluster
                    .mapValues(Query3::computeClusterCentroid)
                    .sortByKey().map(x -> x._2).collect());
            Double distanceFromOlderCentroids = computeEuclideanDistance(centroids, newCentroids);
            if (distanceFromOlderCentroids < 0.0001) {
                break;
            }
        }
        clusteredTrendCoefficientRegions.map(x -> x._1 + "," + x._2.getRegionName()).saveAsTextFile(outputDirectory);

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
        // TODO possibly modify in case of header handling at ingestion-time
        JavaRDD<String> rddInputWithoutHeader = inputRDD.filter(line -> !line.contains("Province"));
        JavaPairRDD<Integer, MonthlyRegionData> monthlyRegionsData = rddInputWithoutHeader
                .flatMapToPair(Query3::parseInputLine)
                .filter(x -> x._1 != null)
                .cache();

        long numberOfMonths = monthlyRegionsData.groupByKey().count();

        // Apply k-means algorithm to cluster regions with similar behaviour of confirmed cases during a month
        for (int k = 0; k < numberOfMonths; k++){
            final int monthIndex = k;
            JavaPairRDD<Integer, MonthlyRegionData> kthMonthRegionData =
                    monthlyRegionsData.filter(x -> x._1 == monthIndex);

            List<Tuple2<Double, String>> top50MonthlyAffectedRegion = kthMonthRegionData
                    .mapToPair(x -> new Tuple2<>(x._2.getTrendLineCoefficient(), x._2.getName()))
                    .top(50, new TrendLineMonthlyRegionComparator());
            // TODO replace pair with object
            JavaPairRDD<Double, String> top50MonthlyAffectedRegionRDD =
                    sc.parallelizePairs(top50MonthlyAffectedRegion).cache();

            if (mode.equals("naive")) {
                kMeansNaive(top50MonthlyAffectedRegionRDD, args[1] + "/month" + monthIndex);
            } else {
                kMeansMLib(sc, top50MonthlyAffectedRegionRDD, args[1] + "/month" + monthIndex);
            }


        }

        sc.stop();
    }

}
