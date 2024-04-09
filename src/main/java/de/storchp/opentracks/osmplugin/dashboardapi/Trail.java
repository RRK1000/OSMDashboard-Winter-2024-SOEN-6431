package de.storchp.opentracks.osmplugin.dashboardapi;

import java.util.ArrayList;
import java.util.List;


/**
 * Represents geographical bounds with minimum and maximum latitude and longitude.
 */
class Bounds {
    public double minlat;
    public double minlon;
    public double maxlat;
    public double maxlon;

    // Constructor
    public Bounds(double minlat, double minlon, double maxlat, double maxlon) {
        this.minlat = minlat;
        this.minlon = minlon;
        this.maxlat = maxlat;
        this.maxlon = maxlon;
    }

    public Bounds(){

    }
}


/**
 * Represents a trail containing a list of SkiElements.
 */
public class Trail {

    private List<SkiElements> trails = new ArrayList<>();

    private static Trail instance = null;

    private Trail() {
    }


    /**
     * Returns a list of trail names extracted from SkiElements.
     * @return A list of trail names.
     */
    public List<String> exposeTrailNames() {
        List<String> trailNames = new ArrayList<>();
        for (SkiElements element : trails) {
            trailNames.add(element.tags.name);
        }
        return trailNames;
    }

    public void setTrails(List<SkiElements> trails) {
        this.trails = trails;
    }

    public static void setInstance(Trail instance) {
        Trail.instance = instance;
    }

    @Override
    public String toString() {
        return "Trail{" +
                "trails=" + trails +
                '}';
    }

    // Static method to create instance of Singleton class
    public static synchronized Trail getInstance() {
        if (instance == null)
            instance = new Trail();

        return instance;
    }




    public List<SkiElements> getTrails() {
        return trails;
    }

    public void addTrailData(SkiElements trail) {
        trails.add(trail);
    }

    public void clearData() {
        trails.clear();
    }

}

class Geometry {
    public double lat;
    public double lon;


    // Constructor
    public Geometry(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public Geometry(){

    }
}

class Tags {
    public String name;
    public String piste_difficulty;
    public String piste_grooming;
    public String piste_type;
    public String ref;
    public String piste_ref;
    public String foot;
    public String route;
    public String gladed;

    public Tags(String name, String piste_difficulty, String piste_grooming,
                String piste_type, String ref, String piste_ref,
                String foot, String route, String gladed) {
        this.name = name;
        this.piste_difficulty = piste_difficulty;
        this.piste_grooming = piste_grooming;
        this.piste_type = piste_type;
        this.ref = ref;
        this.piste_ref = piste_ref;
        this.foot = foot;
        this.route = route;
        this.gladed = gladed;
    }


    public Tags(){

    }
}

