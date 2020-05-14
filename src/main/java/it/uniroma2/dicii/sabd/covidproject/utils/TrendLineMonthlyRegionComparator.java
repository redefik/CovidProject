package it.uniroma2.dicii.sabd.covidproject.utils;

import it.uniroma2.dicii.sabd.covidproject.datamodel.RegionData;
import scala.Tuple2;

import java.io.Serializable;
import java.util.Comparator;

public class TrendLineMonthlyRegionComparator implements Comparator<Tuple2<Double, RegionData>> , Serializable {

    @Override
    public int compare(Tuple2<Double, RegionData> o1, Tuple2<Double, RegionData> o2) {
        if (o1._1 - o2._1 > 0)
            return 1;
        else if (o1._1 - o2._1 < 0)
            return -1;
        else
            return 0;
    }
}
