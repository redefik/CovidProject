package it.uniroma2.dicii.sabd.covidproject.utils;

// TODO CONSIDER THE IDEA TO MAKE IT A BROADCAST VARIABLE
/*
 * This utility class provides a method to identify a continent by a <latitude, longitude> pair.
 * The check is made using Polygon Java class.
 * */

import java.awt.*;

public class UtilsContinent
{

    public enum Continent{

        AFRICA, AMERICA, ANTARCTICA, ASIA, EUROPE, OCEANIA, NA

    }

    /* Each polygon represents a continent or a fraction of it */
    Polygon northAmerica;
    Polygon southAmerica;
    Polygon africa;
    Polygon europe;
    Polygon asia1;
    Polygon asia2;
    Polygon oceania1;
    Polygon oceania2;
    Polygon antarctica;

    public UtilsContinent() {
        northAmerica = new Polygon();
        northAmerica.addPoint(-169,90);
        northAmerica.addPoint( -10, 90);
        northAmerica.addPoint(-10,78 );
        northAmerica.addPoint(-37, 57 );
        northAmerica.addPoint(-30, 15);
        northAmerica.addPoint(-75, 15 );
        northAmerica.addPoint(-82, 1);
        northAmerica.addPoint(-105,1 );
        northAmerica.addPoint(-180,51 );
        northAmerica.addPoint(-180, 60);
        northAmerica.addPoint(-169, 60);
        southAmerica = new Polygon();
        southAmerica.addPoint(-105, 1);
        southAmerica.addPoint(-82, 1);
        southAmerica.addPoint(-75, 15);
        southAmerica.addPoint(-30, 15);
        southAmerica.addPoint(-30,-60);
        southAmerica.addPoint(-105,-60);
        africa = new Polygon();
        africa.addPoint(-30, 15);
        africa.addPoint(-13, 28);
        africa.addPoint(-10, 35);
        africa.addPoint(10,38 );
        africa.addPoint(27, 33);
        africa.addPoint(34, 32);
        africa.addPoint( 35, 29);
        africa.addPoint(34, 28);
        africa.addPoint(44, 11);
        africa.addPoint(52, 12);
        africa.addPoint(75, -60);
        africa.addPoint(-30, -60);
        europe = new Polygon();
        europe.addPoint(-10, 90);
        europe.addPoint(77, 90);
        europe.addPoint(49, 42);
        europe.addPoint(30, 42);
        europe.addPoint(29, 41);
        europe.addPoint(29, 41);
        europe.addPoint(27, 41);
        europe.addPoint(27, 40);
        europe.addPoint(26, 40);
        europe.addPoint(25, 39);
        europe.addPoint(28, 35);
        europe.addPoint(27, 33);
        europe.addPoint(10, 38);
        europe.addPoint(-10, 35);
        europe.addPoint(-13,28);
        europe.addPoint(-30,15);
        europe.addPoint(-37,57);
        europe.addPoint(-10,78);
        asia1 = new Polygon();
        asia1.addPoint(77,90);
        asia1.addPoint( 49,42);
        asia1.addPoint( 30,42);
        asia1.addPoint( 29,41);
        asia1.addPoint(29,41);
        asia1.addPoint( 27,41);
        asia1.addPoint( 27,40);
        asia1.addPoint( 26,40);
        asia1.addPoint( 25,39);
        asia1.addPoint( 28,35);
        asia1.addPoint( 27,33);
        asia1.addPoint( 35,32);
        asia1.addPoint( 35,29);
        asia1.addPoint( 34,28);
        asia1.addPoint( 44,11);
        asia1.addPoint( 52,12);
        asia1.addPoint( 75,-60);
        asia1.addPoint( 110,-60);
        asia1.addPoint( 110,-32);
        asia1.addPoint( 110,-12);
        asia1.addPoint( 140,-10);
        asia1.addPoint( 140,33);
        asia1.addPoint( 167,51);
        asia1.addPoint( 180,60);
        asia1.addPoint( 180,90);
        asia2 = new Polygon();
        asia2.addPoint(-180,90);
        asia2.addPoint(-169,90);
        asia2.addPoint(-169, 60);
        asia2.addPoint(-180,60);
        antarctica = new Polygon();
        antarctica.addPoint(-180, -60);
        antarctica.addPoint(180, -60);
        antarctica.addPoint(180, -90);
        antarctica.addPoint(-180, -90);
        oceania1 = new Polygon();
        oceania1.addPoint(105,-60);
        oceania1.addPoint(180, -60);
        oceania1.addPoint(180, 60);
        oceania1.addPoint(165, 60);
        oceania1.addPoint(140, 30);
        oceania1.addPoint(140,-15);
        oceania1.addPoint(105, -15);
        oceania2 = new Polygon();
        oceania2.addPoint(-180, -60);
        oceania2.addPoint(-105,-60);
        oceania2.addPoint(-105,0);
        oceania2.addPoint(-180, 45);

    }

    public Continent getContinentFromLatLong(double latitude, double longitude) {
        Point point = new Point((int)longitude, (int)latitude);
        if (africa.contains(point)) {
            return Continent.AFRICA;
        }
        if (northAmerica.contains(point) || southAmerica.contains(point)) {
            return Continent.AMERICA;
        }
        if (antarctica.contains(point)) {
            return Continent.ANTARCTICA;
        }
        if (asia1.contains(point) || asia2.contains(point)) {
            return Continent.ASIA;
        }
        if (europe.contains(point)) {
            return Continent.EUROPE;
        }
        if (oceania1.contains(point) || oceania2.contains(point)) {
            return Continent.OCEANIA;
        }
        return Continent.NA;
    }

}
