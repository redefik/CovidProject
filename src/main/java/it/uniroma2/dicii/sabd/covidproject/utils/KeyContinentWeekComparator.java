package it.uniroma2.dicii.sabd.covidproject.utils;

import java.io.Serializable;
import java.util.Comparator;

/*
* Utility class used to sort the result of query 2
* */
public class KeyContinentWeekComparator implements Comparator<String>, Serializable {
    @Override
    public int compare(String o1, String o2) {
        String[] fields1 = o1.split(",");
        String[] fields2 = o2.split(",");
        String continent1= fields1[0];
        String continent2=fields2[0];
        Integer week1 = Integer.parseInt(fields1[1]);
        Integer week2 = Integer.parseInt(fields2[1]);
        if (continent1.equals(continent2)) {
            return week1 - week2;
        }
        return continent1.compareTo(continent2);
    }
}
