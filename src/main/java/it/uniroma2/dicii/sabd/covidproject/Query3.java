package it.uniroma2.dicii.sabd.covidproject;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import it.uniroma2.dicii.sabd.covidproject.datamodel.RegionData;
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

/*
 * This query is based on a dataset on Covid-19 provided by the Center for Systems Science and Engineering at
 * John Hopkins University, USA. The dataset, in CSV format, reports the current number of confirmed cases of Covid in the world.
 * In detail, each row contains: a Country, optionally the corresponding Province/State, Latitude and Longitude and
 * the daily columns with total number of confirmed cases, starting from January 22, 2020. Note that available data are
 * cumulative. For further details, see:
 * https://github.com/CSSEGISandData/COVID-19/blob/master/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_global.csv#L11
 *
 * The aim of the query is to identify the top-50 affected regions using the trendline coefficient and
 * for each month use K-means clustering algorithm (with K = 4) to identify the regions belonging to each
 * cluster with respect to the trend of confirmed cases.
 *
 * The query is answered using Apache Spark.
 */


public class Query3 {

    private final static int NUM_OF_K_MEANS_CLUSTERS = 4;
    private final static int MAX_K_MEANS_ITERATIONS = 20;
    private final static double K_MEANS_ERROR_THRESHOLD = 0.0001;
    private final static long K_MEANS_MLLIB_SEED = 1L;

    /* Parse a line of the CSV dataset */
    private static Iterator<Tuple2<Integer, RegionData>> parseInputLine (String line){

        try {
            /* Extract fields of interest from CSV line */
            CSVReader csvReader = new CSVReader(new StringReader(line));
            String[] csvFields = csvReader.readNext();
            csvReader.close();
            /* If province/state is not available, the country is considered */
            String regionName = csvFields[0].equals("") ? csvFields[1] : csvFields[0];
            /* Retrieve number of days available for computations of daily increments of confirmed cases.
             * Only completed months are considered and the first day is not considered since the increment is not
             * computable. The months duration is assumed to be 30 days for sake of simplicity */
            int availableDays = ((csvFields.length - 5) - ((csvFields.length - 5) % 30));
            /* Convert cumulative data to daily increments of confirmed cases */
            Double[] confirmedDailyIncrements = GlobalDataUtils.convertCumulativeToIncrement(availableDays, csvFields);
            int numberOfMonths = confirmedDailyIncrements.length / 30;
            /* For each month, a RegionData object, representing the monthly trend line coefficient of a region,
             * is produced */
            List<Tuple2<Integer, RegionData>> output = new ArrayList<>();
            for (int i = 0; i < numberOfMonths; i++) {
                Double[] monthlyIncrements = new Double[30];
                System.arraycopy(confirmedDailyIncrements, i * 30, monthlyIncrements, 0, 30);
                RegionData monthlyRegionData = new RegionData();
                monthlyRegionData.setName(regionName);
                monthlyRegionData.setTrendLineCoefficient(GlobalDataUtils.computeCoefficientEstimate(monthlyIncrements));
                output.add(new Tuple2<>(i, monthlyRegionData));
            }
            return output.iterator();

        } catch (CsvValidationException | IOException e) {
            List<Tuple2<Integer, RegionData>> failedOutput = new ArrayList<>();
            failedOutput.add(new Tuple2<>(null, null));
            return failedOutput.iterator();
        }
    }

    /*
     * Apply a MLlib implementation of k-means. For the details of this implementation, see Apache Spark documentation
     * */
    private static void kMeansMLlib(JavaSparkContext sc, JavaPairRDD<Double,String> trendRegionPairs, String outputDirectory) {
        SparkSession ss = SparkSession.builder().config(sc.getConf()).getOrCreate();
        /* Convert input RDD to DataFrame */
        Dataset<Row> trendRegionsDF = ss.createDataFrame(trendRegionPairs.map(x ->
                {
                    RegionData regionData = new RegionData();
                    regionData.setName(x._2);
                    regionData.setTrendLineCoefficient(x._1);
                    return regionData;
                }),
                RegionData.class);
        /* Set feature column in dataset */
        String[] featureCols = {"trendLineCoefficient"};
        VectorAssembler assembler = new VectorAssembler().setInputCols(featureCols).setOutputCol("features");
        Dataset<Row> featuredDF = assembler.transform(trendRegionsDF);
        /* Apply K-Means */
        KMeans kMeans = new KMeans().setK(NUM_OF_K_MEANS_CLUSTERS).setSeed(K_MEANS_MLLIB_SEED);
        KMeansModel model = kMeans.fit(featuredDF);
        Dataset<Row> clusteredRegions = model.transform(featuredDF);
        /* Save clustering results in a CSV file */
        clusteredRegions.select("prediction","name","trendLineCoefficient")
                .write().format("csv").option("header", "false").save(outputDirectory);
    }

    /*
    * Given a trend coefficient value and a vector of centroids, identify the index of the centroid at minimum distance
    * */
    public static Tuple2<Integer, RegionData> assignRegionToCluster(Tuple2<Double,String> trendRegion, Double[] centroids) {
        int minDistanceCentroid = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < centroids.length; i++) {
            double distance = Math.abs(centroids[i] - trendRegion._1);
            if (distance < minDistance) {
                minDistance = distance;
                minDistanceCentroid = i;
            }
        }
        RegionData regionData = new RegionData();
        regionData.setName(trendRegion._2);
        regionData.setTrendLineCoefficient(trendRegion._1);
        return new Tuple2<>(minDistanceCentroid, regionData);
    }

    /*
    * Given a list of trend coefficient values belonging to the same cluster, computes the centroid of the cluster
    * */
    public static Double computeClusterCentroid(Iterable<RegionData> clusterPoints) {
        double sum = 0.0;
        int count = 0;
        for (RegionData rd : clusterPoints) {
            sum += rd.getTrendLineCoefficient();
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

    /*
    * Apply a naive implementation of k-means. This implementation has the following characteristics:
    * - The initial centroids are selected dividing the range between the minimum value and maximum value
    *   in k parts and assigning to cluster i the ((2i-1)/2k)*100th-percentile
    * - The training ends after a maximum of MAX_K_MEANS_ITERATIONS iterations or if the distance
    *   between the vector of new centroids and the vector of old centroids is lower than K_MEANS_ERROR_THRESHOLD
    * - Euclidean distance is used
    * */
    public static void kMeansNaive(JavaPairRDD<Double,String> trendRegionPairs, String outputDirectory) {

        List<Double> trendCoefficients = trendRegionPairs.map(x -> x._1).distinct().collect();
        double[] trendCoefficientsArray = new double[trendCoefficients.size()];
        for (int i = 0; i < trendCoefficients.size(); i++) {
            trendCoefficientsArray[i] = trendCoefficients.get(i);
        }
        Double[] newCentroids = new Double[NUM_OF_K_MEANS_CLUSTERS];
        for (int i = 0; i < NUM_OF_K_MEANS_CLUSTERS; i++) {
            newCentroids[i] = StatUtils.percentile(trendCoefficientsArray, (float) 100 * (2 * (i + 1) - 1)/(2 * NUM_OF_K_MEANS_CLUSTERS));
        }
        JavaPairRDD<Integer, RegionData> clusteredTrendCoefficientRegions = null;
        for (int i = 0; i < MAX_K_MEANS_ITERATIONS; i++) {
            Double[] centroids = newCentroids;
            clusteredTrendCoefficientRegions = trendRegionPairs.mapToPair(x -> assignRegionToCluster(x, centroids));
            JavaPairRDD<Integer, Iterable<RegionData>> trendRegionsByCluster = clusteredTrendCoefficientRegions.groupByKey();
            newCentroids = fromDoubleListToDoubleArray(trendRegionsByCluster
                    .mapValues(Query3::computeClusterCentroid)
                    .sortByKey().map(x -> x._2).collect());
            Double distanceFromOlderCentroids = computeEuclideanDistance(centroids, newCentroids);
            if (distanceFromOlderCentroids < K_MEANS_ERROR_THRESHOLD) {
                break;
            }
        }
        /* clusteredTrendCoefficientRegions is recomputed from trendRegionPairs but caching is not worthwhile since
        *  this re-computation is done only once in the algorithm and implies the execution of a single transformation */
        clusteredTrendCoefficientRegions.map(x -> x._1 + "," + x._2.getName()).saveAsTextFile(outputDirectory);

    }


    public static void main(String[] args) {

        if (args.length != 3) {
            System.err.println("Input file, output directory and k-means mode required");
            System.exit(1);
        }

        /* The application supports two operating modes:
        * - naive: apply K-means using a naive implementation based on Spark core
        * - mllib: apply K-means leveraging Spark MLlib implementation
        * */
        String mode = args[2];
        if (!mode.equals("naive") && !(mode.equals("mllib"))) {
            System.err.println("Only \"naive\" and \"mllib\" mode are available");
            System.exit(2);
        }

        /* Spark setup */
        SparkConf conf = new SparkConf().setAppName("Query-3");
        JavaSparkContext sc = new JavaSparkContext(conf);
        /* Import input file */
        JavaRDD<String> inputRDD = sc.textFile(args[0]);
        // TODO possibly modify in case of header handling at ingestion-time
        JavaRDD<String> rddInputWithoutHeader = inputRDD.filter(line -> !line.contains("Province"));
        /*
        * Extract from each input line an <k,v> pair where k is the index of the month and v the corresponding
        * RegionData object
        *  */
        JavaPairRDD<Integer, RegionData> monthlyRegionsData = rddInputWithoutHeader
                .flatMapToPair(Query3::parseInputLine)
                .filter(x -> x._1 != null)
                .cache();
        long numberOfMonths = monthlyRegionsData.groupByKey().count();
        /* For each month, the top-50 affected regions are retrieved and k-means clustering is applied to them
        * in order to group regions with a similar trend of confirmed cases during the same month */
        for (int k = 0; k < numberOfMonths; k++){
            final int monthIndex = k;
            JavaPairRDD<Integer, RegionData> kthMonthRegionData =
                    monthlyRegionsData.filter(x -> x._1 == monthIndex);
            List<Tuple2<Double, String>> top50MonthlyAffectedRegion = kthMonthRegionData
                    .mapToPair(x -> new Tuple2<>(x._2.getTrendLineCoefficient(), x._2.getName()))
                    .top(50, new TrendLineMonthlyRegionComparator());
            // TODO replace string with object
            JavaPairRDD<Double, String> top50MonthlyAffectedRegionRDD =
                    sc.parallelizePairs(top50MonthlyAffectedRegion).cache();
            /* A different implementation of k-means is invoked on the basis of the provided parameter */
            if (mode.equals("naive")) {
                kMeansNaive(top50MonthlyAffectedRegionRDD, args[1] + "/month" + monthIndex);
            } else {
                kMeansMLlib(sc, top50MonthlyAffectedRegionRDD, args[1] + "/month" + monthIndex);
            }

        }
        /* Spark shutdown*/
        sc.stop();
    }

}
