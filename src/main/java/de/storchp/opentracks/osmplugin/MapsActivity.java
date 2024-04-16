package de.storchp.opentracks.osmplugin;


import static android.util.TypedValue.COMPLEX_UNIT_PT;
import static java.util.Comparator.comparingInt;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import org.json.JSONObject;
import org.json.JSONArray;
import org.oscim.android.MapPreferences;
import org.oscim.backend.CanvasAdapter;
import org.oscim.core.BoundingBox;
import org.oscim.core.GeoPoint;
import org.oscim.layers.GroupLayer;
import org.oscim.layers.PathLayer;
import org.oscim.layers.marker.ItemizedLayer;
import org.oscim.layers.marker.MarkerInterface;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.layers.marker.MarkerSymbol;
import org.oscim.layers.tile.bitmap.BitmapTileLayer;
import org.oscim.layers.tile.buildings.BuildingLayer;
import org.oscim.layers.tile.vector.VectorTileLayer;
import org.oscim.layers.tile.vector.labeling.LabelLayer;
import org.oscim.map.Map;

import de.storchp.opentracks.osmplugin.dashboardapi.TrackPointsBySegments;
import de.storchp.opentracks.osmplugin.maps.TrailSelectionMapView;

import org.oscim.renderer.BitmapRenderer;
import org.oscim.renderer.GLViewport;
import org.oscim.scalebar.DefaultMapScaleBar;
import org.oscim.scalebar.ImperialUnitAdapter;
import org.oscim.scalebar.MapScaleBar;
import org.oscim.scalebar.MapScaleBarLayer;
import org.oscim.scalebar.MetricUnitAdapter;
import org.oscim.theme.IRenderTheme;
import org.oscim.theme.StreamRenderTheme;
import org.oscim.theme.ThemeFile;
import org.oscim.theme.VtmThemes;
import org.oscim.theme.ZipRenderTheme;
import org.oscim.theme.ZipXmlThemeResourceProvider;
import org.oscim.tiling.source.OkHttpEngine;
import org.oscim.tiling.source.bitmap.DefaultSources;
import org.oscim.tiling.source.mapfile.MapFileTileSource;
import org.oscim.tiling.source.mapfile.MultiMapFileTileSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

import de.storchp.opentracks.osmplugin.dashboardapi.APIConstants;
import de.storchp.opentracks.osmplugin.dashboardapi.ChairLift;
import de.storchp.opentracks.osmplugin.dashboardapi.ChairLiftElements;
import de.storchp.opentracks.osmplugin.dashboardapi.SkiElements;
import de.storchp.opentracks.osmplugin.dashboardapi.SegmentFinder;
import de.storchp.opentracks.osmplugin.dashboardapi.Track;
import de.storchp.opentracks.osmplugin.dashboardapi.TrackPoint;
import de.storchp.opentracks.osmplugin.dashboardapi.Trail;
import de.storchp.opentracks.osmplugin.dashboardapi.Waypoint;
import de.storchp.opentracks.osmplugin.databinding.ActivityMapsBinding;
import de.storchp.opentracks.osmplugin.maps.MovementDirection;
import de.storchp.opentracks.osmplugin.maps.StyleColorCreator;
import de.storchp.opentracks.osmplugin.utils.MapMode;
import de.storchp.opentracks.osmplugin.utils.MapUtils;
import de.storchp.opentracks.osmplugin.utils.PreferencesUtils;
import de.storchp.opentracks.osmplugin.utils.StatisticElement;
import de.storchp.opentracks.osmplugin.utils.TrackColorMode;
import de.storchp.opentracks.osmplugin.utils.TrackPointsDebug;
import de.storchp.opentracks.osmplugin.utils.TrackStatistics;
import okhttp3.Cache;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MapsActivity extends BaseActivity implements ItemizedLayer.OnItemGestureListener<MarkerInterface> {

    private static final String TAG = MapsActivity.class.getSimpleName();
    public static final String EXTRA_MARKER_ID = "marker_id";
    private static final int MAP_DEFAULT_ZOOM_LEVEL = 12;
    private static final String EXTRAS_PROTOCOL_VERSION = "PROTOCOL_VERSION";
    private static final String EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK = "EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK";
    private static final String EXTRAS_SHOULD_KEEP_SCREEN_ON = "EXTRAS_SHOULD_KEEP_SCREEN_ON";
    private static final String EXTRAS_SHOW_WHEN_LOCKED = "EXTRAS_SHOULD_KEEP_SCREEN_ON";
    private static final String EXTRAS_SHOW_FULLSCREEN = "EXTRAS_SHOULD_FULLSCREEN";
    private boolean isOpenTracksRecordingThisTrack;
    private ActivityMapsBinding binding;
    private Map map;
    private List<TrackPoint> trackPoints = new ArrayList<>();

    private MapPreferences mapPreferences;
    private IRenderTheme renderTheme;
    private BoundingBox boundingBox;
    private GroupLayer polylinesLayer;
    private ItemizedLayer waypointsLayer;
    private long lastWaypointId = 0;
    private long lastTrackPointId = 0;
    private long lastTrackId = 0;
    private int trackColor;
    private PathLayer polyline;
    private MarkerItem endMarker = null;
    private StyleColorCreator colorCreator = null;
    private GeoPoint startPos;
    private GeoPoint endPos;
    private boolean fullscreenMode = false;
    private MovementDirection movementDirection = new MovementDirection();
    private MapMode mapMode;
    private OpenTracksContentObserver contentObserver;
    private Uri tracksUri;
    private Uri trackPointsUri;
    private Uri waypointsUri;
    private int strokeWidth;
    private int protocolVersion = 1;
    private TrackPointsDebug trackPointsDebug;
    private List<Track> storedTracksData = new ArrayList<>();
    private TrackPointsBySegments storedTrackPointsBySegments;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        strokeWidth = 8;
        //strokeWidth = PreferencesUtils.getStrokeWidth();
        mapMode = PreferencesUtils.getMapMode();

        map = binding.map.mapView.map();
        mapPreferences = new MapPreferences(MapsActivity.class.getName(), this);

        setSupportActionBar(binding.toolbar.mapsToolbar);

        createMapViews();
        createLayers();
        map.getMapPosition().setZoomLevel(MAP_DEFAULT_ZOOM_LEVEL);

        binding.map.fullscreenButton.setOnClickListener(v -> switchFullscreen());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            public void handleOnBackPressed() {
                navigateUp();
            }
        });

        // Get the intent that started this activity
        var intent = getIntent();
        if (intent != null) {
            onNewIntent(intent);
        }

        ((TrailSelectionMapView) binding.map.mapView).setOnMapTouchListener(geoPoint -> {
            // Assuming you have a method getSegments() that returns a List of segment objects
            // Each segment object should have a start and end GeoPoint
            List<Segment> segments = getSegments(); // You need to implement this method based on your data structure
            resetMapData();
            Segment closestSegment = null;
            Segment nextSegment = null;
            int segmentNumber = 0;
            double minDistance = Double.MAX_VALUE;
            for (int i = 0; i < segments.size(); i++) {
                double distance = SegmentFinder.distanceToSegment(segments.get(i).start, segments.get(i).end, geoPoint);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestSegment = segments.get(i);
                    segmentNumber = i;
                    if((i+1)<segments.size()){
                        nextSegment = segments.get(i+1);
                    }

                }
            }

            if (closestSegment != null) {
                Log.d("MapsActivity", "Closest segment start: " + closestSegment.start.getLatitude() + "," + closestSegment.start.getLongitude() +
                        " end: " + closestSegment.end.getLatitude() + "," + closestSegment.end.getLongitude());
                resetMapData();
                if (polylinesLayer != null) {
                    map.layers().remove(polylinesLayer);
                }
                TrackPoint selectedSegmentInTrack = findSegmentClosestToSelectedSegment(closestSegment);
                TrackPoint nextSelectedSegment = findSegmentClosestToSelectedSegment(nextSegment);
                var trackColorMode = PreferencesUtils.getTrackColorMode();
                int segmentColor = trackColor;
                int currentStrokeWidth = Math.max(strokeWidth, 4);
                if(trackColorMode == TrackColorMode.BY_SPEED && this.storedTrackPointsBySegments != null){
                    double average = this.storedTrackPointsBySegments.calcAverageSpeed();
                    double maxSpeed = this.storedTrackPointsBySegments.calcMaxSpeed();
                    double averageToMaxSpeed = maxSpeed - average;
                    segmentColor = MapUtils.getTrackColorBySpeed(average, averageToMaxSpeed, selectedSegmentInTrack);

                }

                polyline = new PathLayer(map, segmentColor, currentStrokeWidth); // Adjust color and stroke width as needed

                // Add start and end points to the PathLayer
                polyline.addPoint(closestSegment.start);
                polyline.addPoint(closestSegment.end);
                // Add the PathLayer to the map
                map.layers().add(polyline);
                // Optionally, animate the map view to center on the segment
                map.animator().animateTo(closestSegment.start);
                                MarkerSymbol startMarkerSymbol = MapUtils.createMarkerSymbol(
                        this,
                        R.drawable.ic_marker_red_pushpin_modern,
                        false,
                        MarkerSymbol.HotspotPlace.BOTTOM_CENTER
                );
                MarkerSymbol endMarkerSymbol = MapUtils.createMarkerSymbol(
                        this,
                        R.drawable.ic_marker_green_pushpin_modern,
                        false,
                        MarkerSymbol.HotspotPlace.BOTTOM_CENTER
                );

                MarkerItem startMarker = new MarkerItem("Start", "Start", closestSegment.start);
                startMarker.setMarker(startMarkerSymbol);
                MarkerItem endMarker = new MarkerItem("End", "End", closestSegment.end);
                endMarker.setMarker(endMarkerSymbol);
                waypointsLayer.addItem(startMarker);
                waypointsLayer.addItem(endMarker);
                
                String intentAction = getIntent().getAction();
                if (Objects.nonNull(intentAction) && intentAction.equals(APIConstants.ACTION_DASHBOARD)) {
                    displaySelectedTrailTable(selectedSegmentInTrack,nextSelectedSegment);
                }
            }
        });

    }
    private TrackPoint findSegmentClosestToSelectedSegment(Segment closestSegment) {
        TrackPoint selectedSegmentInTrack = null;
        List<TrackPoint> storedSegments = storedTrackPointsBySegments.segments().get(0);
        for(TrackPoint trackPoint: storedSegments){
            GeoPoint trackPointGeoPoint = trackPoint.getLatLong();
            if(trackPointGeoPoint.getLatitude() == closestSegment.start.getLatitude() || trackPointGeoPoint.getLongitude() == closestSegment.start.getLongitude()){
                selectedSegmentInTrack = trackPoint;
            }
        }

        return selectedSegmentInTrack;
    }

    private void displaySelectedTrailTable(TrackPoint selectedSegmentInTrack, TrackPoint nextSelectedSegment) {
        TableLayout tableLayout = createTableLayout(selectedSegmentInTrack,nextSelectedSegment);

        // Get the root layout of the activity
        ViewGroup rootLayout = findViewById(android.R.id.content);

        // Create layout parameters for the table
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.gravity = Gravity.BOTTOM; // Position the table at the bottom of the screen

        // Set margins for the table layout (optional)
        int margin = getResources().getDimensionPixelSize(R.dimen.table_padding);
        layoutParams.setMargins(margin, margin, margin, margin);

        // Set padding and background color for the table layout
        margin = getResources().getDimensionPixelSize(R.dimen.table_padding);
        tableLayout.setPadding(margin, margin, margin, margin);
        tableLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.table_background_color));
        tableLayout.setBackgroundResource(R.drawable.ic_table_border);

        // Add the table layout to the root layout with the specified layout parameters
        rootLayout.addView(tableLayout, layoutParams);
    }

    private TableLayout createTableLayout(TrackPoint selectedSegmentInTrack,TrackPoint nextSegment) {
        // Create a new TableLayout
        TableLayout tableLayout = new TableLayout(this);
        tableLayout.setLayoutParams(new TableLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        tableLayout.setBackgroundResource(R.drawable.ic_table_border); // Define a drawable resource for table borders
        populateSelectedTrailDetails(selectedSegmentInTrack, tableLayout, nextSegment);
        return tableLayout;
    }

    private void populateSelectedTrailDetails(TrackPoint selectedSegmentInTrack, TableLayout tableLayout, TrackPoint nextSegment) {
        List<Track> tracksData = getTracksDataForTable();
        Track trackToBePopulated = tracksData.get(0);
        drawTableLine(tableLayout);
        long differenceInMilliseconds = nextSegment.getTime().getTime() - selectedSegmentInTrack.getTime().getTime();

// Convert the difference from milliseconds to minutes
        long differenceInMinutes = differenceInMilliseconds / (60 * 1000);

// If you want to get the difference in a specific String format like "XX min XX sec", you can do:
        long differenceInSeconds = differenceInMilliseconds / 1000; // total seconds
        long seconds = differenceInSeconds % 60; // remaining seconds
        String formattedDifference = Math.abs(differenceInMinutes) + " min " + Math.abs(seconds) + " sec";
//        Log.d("checkoutput",String.valueOf(selectedSegmentInTrack.getDistance()));
        TableRow headerRow = new TableRow(this);
        headerRow.setLayoutParams(new TableRow.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Create TextViews for header row
        TextView attributeHeader = createTableCell("Attribute");
        TextView detailsHeader = createTableCell("Details");
        attributeHeader.setTypeface(null, Typeface.BOLD);
        detailsHeader.setTypeface(null, Typeface.BOLD);
        headerRow.addView(attributeHeader);
        headerRow.addView(detailsHeader);
        tableLayout.addView(headerRow);
        drawTableLine(tableLayout);


        double totalDistanceMeter = trackToBePopulated.totalDistanceMeter();
        double totalDistanceKm = totalDistanceMeter / 1000; // Convert meters to kilometers
        String formattedTotalDistance = String.format("%.2f", totalDistanceKm);


        //calculate average speed for whole track.
        double avgSpeedInMeterPerSec = getAvgSpeed(trackToBePopulated);
        //converting average speed in Km/Hr from m/s.
        double avgSpeedInKmPerHour = getSpeedInKmPerHour(avgSpeedInMeterPerSec);
        //formatting speed to show in table.
        String formattedAvgSpeedInKmPerHour = formatSpeed(avgSpeedInKmPerHour);

        //getting speed for individual segment
        double speedForSegment = getSegmentSpeed(selectedSegmentInTrack);
        //converting segment speed in Km/Hr from m/s.
        double segmentSpeedInKmPerHour = getSpeedInKmPerHour(speedForSegment);
        //formatting segment speed to show in table.
        String formattedSegmentSpeedInKmPerHour = formatSpeed(segmentSpeedInKmPerHour);

        double timeInSecondsForSegment = differenceInMilliseconds / 1000.0; // Use the actual time difference for the segment

// Calculate the distance for the segment using the segment speed (in meters)
        double distanceForSegment = Math.abs(speedForSegment * timeInSecondsForSegment);




        createTableRow("Trail Name", trackToBePopulated.trackname(), tableLayout);
        createTableRow("Trail Distance", formattedTotalDistance + " km", tableLayout);
        createTableRow("Trail Elevation", trackToBePopulated.maxElevationMeter() + " m", tableLayout);
        createTableRow("Average Trail Speed", formattedAvgSpeedInKmPerHour + " km/h", tableLayout);
        createTableRow("Time Taken", formattedDifference, tableLayout);
        createTableRow("Segment Number", String.valueOf(selectedSegmentInTrack.getTrackPointId()), tableLayout);
        createTableRow("Segment Speed", formattedSegmentSpeedInKmPerHour + " km/h", tableLayout);
        createTableRow("Slope", String.valueOf(getSlopePercentage(distanceForSegment,trackToBePopulated.maxElevationMeter()))+ "%", tableLayout);
    }


    //average speed for whole track
    private double getAvgSpeed(Track trackToBePopulated){
        return trackToBePopulated.avgMovingSpeedMeterPerSecond();
    }

    //converting  speed in Km/Hr from m/s.
    private double getSpeedInKmPerHour(double speedInMeterPerSec){
        return (speedInMeterPerSec * 3.6);
    }

    //formatting speed to show in table.
    private String formatSpeed(double speedKmPerHour){
        return String.format("%.2f", speedKmPerHour);
    }

    //getting speed for individual segment
    private double getSegmentSpeed(TrackPoint selectedSegmentOfTrack){
        return selectedSegmentOfTrack.getSpeed();
    }




    private double getSlopePercentage(double distance, float elevation) {

        double slopePercentage = (elevation / distance) *100 ;
        if (slopePercentage > 100) {
            slopePercentage = slopePercentage % 100;
        }
        // Format to xx.xx%
        return Math.round(slopePercentage * 100.0) / 100.0;
    }
    /** @author sadiq
     *  Method to validate track information data
     */

    private boolean validateDataFromTracksData(List<Track> tracksData) {
        // Check if tracksData is not null and contains at least one track
        if (tracksData == null || tracksData.isEmpty()) {
            System.out.println("Tracks list does not contain any track data!");
            return false;
        }

        // Get the first track from the list for validation
        Track trackToBeValidated = tracksData.get(0);

        // Check if the track name is not empty
        if (TextUtils.isEmpty(trackToBeValidated.trackname())) {
            System.out.println("Track name is NULL!");
            return false;
        }

        // Check if total distance and max elevation are positive values
        if (trackToBeValidated.totalDistanceMeter() <= 0 && trackToBeValidated.maxElevationMeter() <= 0) {
            System.out.println("Distance and Elevation are invalid!");
            return false;
        }

        // Check if average speed is a valid positive value
        if (trackToBeValidated.avgSpeedMeterPerSecond() < 0) {
            System.out.println("Average speed is invalid!");
            return false;
        }

        // Check if total time is a valid positive value
        if (trackToBeValidated.totalTimeMillis() <= 0) {
            System.out.println("Total time value is invalid!");
            return false;
        }

        return true; // Data passes all validation checks
    }


    private void createTableRow(String headerName, String headerDetails, TableLayout tableLayout) {
        TableRow trailNameRow = new TableRow(this);
        trailNameRow.setLayoutParams(new TableRow.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView trailNameHeader = createTableCell(headerName);
        TextView trailNameValue = createTableCell(headerDetails);
        // Add TextViews to the table trailNameRow
        trailNameRow.addView(trailNameHeader);
        trailNameRow.addView(trailNameValue);
        tableLayout.addView(trailNameRow);
        drawTableLine(tableLayout);
    }

    private void drawTableLine(TableLayout tableLayout) {
        // Add a horizontal line
        View line = new View(this);
        line.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                2 // Height of the line in pixels
        ));
        line.setBackgroundColor(Color.BLACK);
        tableLayout.addView(line);
    }

    private TextView createTableCell(String text) {
        // Create a TextView for a table cell
        TextView textView = new TextView(this);
        textView.setLayoutParams(new TableRow.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        textView.setText(text);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(5, 5, 5, 5); // Add padding to the cell
        textView.setBackgroundColor(ContextCompat.getColor(this, R.color.cell_color)); // Add color to cells
        textView.setTextColor(ContextCompat.getColor(this, R.color.text_color)); // Set text color
        return textView;
    }

    private List<Track> getTracksDataForTable() {
        //TODO: Data expected from Group 16, including tracks details, segments details for each track, speed, chairlift names and other statistics
        return this.storedTracksData;
    }

    private double distanceToSegment(GeoPoint start, GeoPoint end, GeoPoint point) {
        double A = point.getLatitude() - start.getLatitude();
        double B = point.getLongitude() - start.getLongitude();
        double C = end.getLatitude() - start.getLatitude();
        double D = end.getLongitude() - start.getLongitude();

        double dot = A * C + B * D;
        double lenSq = C * C + D * D;
        double param = -1;
        if (lenSq != 0) { // in case of zero length line
            param = dot / lenSq;
        }

        double xx, yy;

        if (param < 0) {
            xx = start.getLatitude();
            yy = start.getLongitude();
        } else if (param > 1) {
            xx = end.getLatitude();
            yy = end.getLongitude();
        } else {
            xx = start.getLatitude() + param * C;
            yy = start.getLongitude() + param * D;
        }

        double dx = point.getLatitude() - xx;
        double dy = point.getLongitude() - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }


    private void switchFullscreen() {
        showFullscreen(!fullscreenMode);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        resetMapData();

        if (APIConstants.ACTION_DASHBOARD.equals(intent.getAction())) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(APIConstants.ACTION_DASHBOARD_PAYLOAD);
            protocolVersion = intent.getIntExtra(EXTRAS_PROTOCOL_VERSION, 1);
            tracksUri = APIConstants.getTracksUri(uris);
            trackPointsUri = APIConstants.getTrackPointsUri(uris);
            waypointsUri = APIConstants.getWaypointsUri(uris);
            keepScreenOn(intent.getBooleanExtra(EXTRAS_SHOULD_KEEP_SCREEN_ON, false));
            showOnLockScreen(intent.getBooleanExtra(EXTRAS_SHOW_WHEN_LOCKED, false));
            showFullscreen(intent.getBooleanExtra(EXTRAS_SHOW_FULLSCREEN, false));
            isOpenTracksRecordingThisTrack = intent.getBooleanExtra(EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK, false);

            readTrackpoints(trackPointsUri, false, protocolVersion);
            readTracks(tracksUri);
            readWaypoints(waypointsUri);
        } else if ("geo".equals(intent.getScheme())) {
            Waypoint.fromGeoUri(intent.getData().toString()).ifPresent(waypoint -> {
                final MarkerItem marker = MapUtils.createTappableMarker(this, waypoint);
                waypointsLayer.addItem(marker);
                map.getMapPosition().setPosition(waypoint.getLatLong());
                map.getMapPosition().setZoomLevel(map.viewport().getMaxZoomLevel());
            });
        }
    }

    private class OpenTracksContentObserver extends ContentObserver {

        private final Uri tracksUri;
        private final Uri trackpointsUri;
        private final Uri waypointsUri;
        private final int protocolVersion;

        public OpenTracksContentObserver(Uri tracksUri, Uri trackpointsUri, Uri waypointsUri, int protocolVersion) {
            super(new Handler());
            this.tracksUri = tracksUri;
            this.trackpointsUri = trackpointsUri;
            this.waypointsUri = waypointsUri;
            this.protocolVersion = protocolVersion;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri == null) {
                return; // nothing can be done without an uri
            }
            if (tracksUri.toString().startsWith(uri.toString())) {
                readTracks(tracksUri);
            } else if (trackpointsUri.toString().startsWith(uri.toString())) {
                readTrackpoints(trackpointsUri, true, protocolVersion);
            } else if (waypointsUri.toString().startsWith(uri.toString())) {
                readWaypoints(waypointsUri);
            }
        }
    }

    private void showFullscreen(boolean showFullscreen) {
        this.fullscreenMode = showFullscreen;
        var decorView = getWindow().getDecorView();
        int uiOptions = decorView.getSystemUiVisibility();
        if (showFullscreen) {
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            binding.map.fullscreenButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_fullscreen_exit_48));
        } else {
            uiOptions &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
            uiOptions &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
            uiOptions &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            binding.map.fullscreenButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_baseline_fullscreen_48));
        }
        binding.toolbar.mapsToolbar.setVisibility(showFullscreen ? View.GONE : View.VISIBLE);
        decorView.setSystemUiVisibility(uiOptions);
    }

    public void navigateUp() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                && isOpenTracksRecordingThisTrack
                && PreferencesUtils.isPipEnabled()) {
            enterPictureInPictureMode();
        } else {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        super.onCreateOptionsMenu(menu, true);
        menu.findItem(R.id.share).setVisible(true);
        return true;
    }

    /**
     * Template method to create the map views.
     */
    protected void createMapViews() {
        binding.map.mapView.setClickable(true);
    }

    protected ThemeFile getRenderTheme() {
        Uri mapTheme = PreferencesUtils.getMapThemeUri();
        if (mapTheme == null) {
            return VtmThemes.DEFAULT;
        }
        try {
            var renderThemeFile = DocumentFile.fromSingleUri(getApplication(), mapTheme);
            assert renderThemeFile != null;
            var themeFileUri = renderThemeFile.getUri();
            if (Objects.requireNonNull(renderThemeFile.getName(), "Theme files must have a name").endsWith(".zip")) {
                var fragment = themeFileUri.getFragment();
                if (fragment != null) {
                    themeFileUri = themeFileUri.buildUpon().fragment(null).build();
                } else {
                    throw new IllegalArgumentException("Fragment missing, which indicates the theme inside the zip file");
                }
                return new ZipRenderTheme(fragment, new ZipXmlThemeResourceProvider(new ZipInputStream(new BufferedInputStream(getContentResolver().openInputStream(themeFileUri)))));
            }
            return new StreamRenderTheme("/assets/", getContentResolver().openInputStream(themeFileUri));
        } catch (Exception e) {
            Log.e(TAG, "Error loading theme " + mapTheme, e);
            return VtmThemes.DEFAULT;
        }
    }

    protected MultiMapFileTileSource getMapFile() {
        MultiMapFileTileSource tileSource = new MultiMapFileTileSource();
        Set<Uri> mapFiles = PreferencesUtils.getMapUris();
        if (mapFiles.isEmpty()) {
            return null;
        }
        var mapsCount = new AtomicInteger(0);
        mapFiles.stream()
                .filter(uri -> DocumentFile.isDocumentUri(this, uri))
                .map(uri -> DocumentFile.fromSingleUri(this, uri))
                .filter(documentFile -> documentFile != null && documentFile.canRead())
                .forEach(documentFile -> readMapFile(tileSource, mapsCount, documentFile));

        if (mapsCount.get() == 0 && !mapFiles.isEmpty()) {
            Toast.makeText(this, R.string.error_loading_offline_map, Toast.LENGTH_LONG).show();
        }

        return mapsCount.get() > 0 ? tileSource : null;
    }

    private void readMapFile(MultiMapFileTileSource mapDataStore, AtomicInteger mapsCount, DocumentFile documentFile) {
        try {
            var inputStream = (FileInputStream) getContentResolver().openInputStream(documentFile.getUri());
            MapFileTileSource tileSource = new MapFileTileSource();
            tileSource.setMapFileInputStream(inputStream);
            mapDataStore.add(tileSource);
            mapsCount.getAndIncrement();
        } catch (Exception e) {
            Log.e(TAG, "Can't open mapFile", e);
        }
    }

    protected void loadTheme() {
        if (renderTheme != null) {
            renderTheme.dispose();
        }
        renderTheme = map.setTheme(VtmThemes.DEFAULT);
    }

    protected void createLayers() {
        var mapFile = getMapFile();

        if (mapFile != null) {
            VectorTileLayer tileLayer = map.setBaseMap(mapFile);
            loadTheme();

            map.layers().add(new BuildingLayer(map, tileLayer));
            map.layers().add(new LabelLayer(map, tileLayer));

            DefaultMapScaleBar mapScaleBar = new DefaultMapScaleBar(map);
            mapScaleBar.setScaleBarMode(DefaultMapScaleBar.ScaleBarMode.BOTH);
            mapScaleBar.setDistanceUnitAdapter(MetricUnitAdapter.INSTANCE);
            mapScaleBar.setSecondaryDistanceUnitAdapter(ImperialUnitAdapter.INSTANCE);
            mapScaleBar.setScaleBarPosition(MapScaleBar.ScaleBarPosition.BOTTOM_LEFT);

            MapScaleBarLayer mapScaleBarLayer = new MapScaleBarLayer(map, mapScaleBar);
            BitmapRenderer renderer = mapScaleBarLayer.getRenderer();
            renderer.setPosition(GLViewport.Position.BOTTOM_LEFT);
            renderer.setOffset(5 * CanvasAdapter.getScale(), 0);
            map.layers().add(mapScaleBarLayer);

            map.setTheme(getRenderTheme());

        } else if (BuildConfig.offline) {
            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_logo_color_24dp)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.no_map_configured)
                    .setPositiveButton(android.R.string.ok, null)
                    .create().show();
        } else if (PreferencesUtils.getOnlineMapConsent()) {
            setOnlineTileLayer();
        } else {
            showOnlineMapConsent();
        }
    }

    private void setOnlineTileLayer() {
        var tileSource = DefaultSources.OPENSTREETMAP.build();
        var builder = new OkHttpClient.Builder();
        var cacheDirectory = new File(getExternalCacheDir(), "tiles");
        int cacheSize = 10 * 1024 * 1024; // 10 MB
        var cache = new Cache(cacheDirectory, cacheSize);
        builder.cache(cache);

        tileSource.setHttpEngine(new OkHttpEngine.OkHttpFactory(builder));
        tileSource.setHttpRequestHeaders(Collections.singletonMap("User-Agent", getString(R.string.app_name) + ":" + BuildConfig.APPLICATION_ID));

        BitmapTileLayer bitmapLayer = new BitmapTileLayer(map, tileSource);
        map.layers().add(bitmapLayer);
    }

    private void showOnlineMapConsent() {
        var message = new SpannableString(getString(R.string.online_map_consent));
        Linkify.addLinks(message, Linkify.ALL);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_logo_color_24dp)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog1, which) -> {
                    PreferencesUtils.setOnlineMapConsent(true);
                    MapsActivity.this.recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        ((TextView) Objects.requireNonNull(dialog.findViewById(android.R.id.message),
                "An AlertDialog must have a TextView with id.message"))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Android Activity life cycle method.
     */
    @Override
    protected void onDestroy() {
        binding.map.mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.map_info) {
            var intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.share) {
            sharePicture();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void sharePicture() {
        // prepare rendering
        var view = binding.map.mainView;
        glSurfaceView = binding.map.mapView;

        binding.map.sharePictureTitle.setText(R.string.share_picture_title);
        binding.map.controls.setVisibility(View.INVISIBLE);
        binding.map.attribution.setVisibility(View.INVISIBLE);

        // draw
        var canvas = new Canvas();
        var toBeCropped = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);

        captureBitmap(canvas::setBitmap);
        view.draw(canvas);

        var bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inTargetDensity = 1;
        toBeCropped.setDensity(Bitmap.DENSITY_NONE);

        int cropFromTop = (int) (70 * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT));
        int fromHere = toBeCropped.getHeight() - cropFromTop;
        var croppedBitmap = Bitmap.createBitmap(toBeCropped, 0, cropFromTop, toBeCropped.getWidth(), fromHere);

        try {
            var sharedFolderPath = new File(this.getCacheDir(), "shared");
            sharedFolderPath.mkdir();
            var file = new File(sharedFolderPath, System.currentTimeMillis() + ".png");
            var out = new FileOutputStream(file);
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.close();
            var share = new Intent(Intent.ACTION_SEND);
            share.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file));
            share.setType("image/png");
            startActivity(Intent.createChooser(share, "send"));
        } catch (Exception exception) {
            Log.e(TAG, "Error sharing Bitmap", exception);
        }

        binding.map.controls.setVisibility(View.VISIBLE);
        binding.map.attribution.setVisibility(View.VISIBLE);
        binding.map.sharePictureTitle.setText("");
    }

    private GLSurfaceView glSurfaceView;
    private Bitmap snapshotBitmap;

    private interface BitmapReadyCallbacks {
        void onBitmapReady(Bitmap bitmap);
    }

    private void captureBitmap(final BitmapReadyCallbacks bitmapReadyCallbacks) {
        glSurfaceView.queueEvent(() -> {
            EGL10 egl = (EGL10) EGLContext.getEGL();
            GL10 gl = (GL10) egl.eglGetCurrentContext().getGL();
            snapshotBitmap = createBitmapFromGLSurface(0, 0, glSurfaceView.getWidth(), glSurfaceView.getHeight(), gl);
            runOnUiThread(() -> bitmapReadyCallbacks.onBitmapReady(snapshotBitmap));
        });

    }

    private Bitmap createBitmapFromGLSurface(int x, int y, int w, int h, GL10 gl) {
        int[] bitmapBuffer = new int[w * h];
        int[] bitmapSource = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);

        try {
            gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            int offset1;
            int offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            Log.e(TAG, "createBitmapFromGLSurface: " + e.getMessage(), e);
            return null;
        }

        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
    }

    private List<Segment> getSegments() {
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < trackPoints.size() - 1; i++) {
            TrackPoint startTrackPoint = trackPoints.get(i);
            TrackPoint endTrackPoint = trackPoints.get(i + 1);

            // Directly use the GeoPoint from your TrackPoint class
            GeoPoint startPoint = startTrackPoint.getLatLong();
            GeoPoint endPoint = endTrackPoint.getLatLong();

            segments.add(new Segment(startPoint, endPoint));
        }
        return segments;
    }


    private void readTrackpoints(Uri data, boolean update, int protocolVersion) {
        Log.i(TAG, "Loading trackpoints from " + data);

        synchronized (map.layers()) {
            var showPauseMarkers = PreferencesUtils.isShowPauseMarkers();
            var latLongs = new ArrayList<GeoPoint>();
            int tolerance = PreferencesUtils.getTrackSmoothingTolerance();
            Log.i(TAG, "in sync " + data);
            try {
                var trackpointsBySegments = TrackPoint.readTrackPointsBySegments(getContentResolver(), data, lastTrackPointId, protocolVersion);
                this.storedTrackPointsBySegments = trackpointsBySegments;
                if (trackpointsBySegments.isEmpty()) {
                    Log.d(TAG, "No new trackpoints received");
                    return;
                }
                double average = trackpointsBySegments.calcAverageSpeed();
                double maxSpeed = trackpointsBySegments.calcMaxSpeed();
                double averageToMaxSpeed = maxSpeed - average;
                var trackColorMode = PreferencesUtils.getTrackColorMode();
                if (isOpenTracksRecordingThisTrack && !trackColorMode.isSupportsLiveTrack()) {
                    trackColorMode = TrackColorMode.DEFAULT;
                }
                for (var trackPoints : trackpointsBySegments.segments()) {
                    Log.i(TAG, "in trackpoints " + data);
                    if (!update) {
                        polyline = null; // cut polyline on new segment
                        if (tolerance > 0) { // smooth track
                            trackPoints = MapUtils.decimate(tolerance, trackPoints);
                        }
                    }
                    for (var trackPoint : trackPoints) {
                        this.trackPoints.add(trackPoint);
                        lastTrackPointId = trackPoint.getTrackPointId();
                        if (trackPoint.getTrackId() != lastTrackId) {
                            if (trackColorMode == TrackColorMode.BY_TRACK) {
                                trackColor = colorCreator.nextColor();
                            }
                            lastTrackId = trackPoint.getTrackId();
                            polyline = null; // reset current polyline when trackId changes
                            startPos = null;
                            endPos = null;
                        }

                        if (trackColorMode == TrackColorMode.BY_SPEED) {
                            trackColor = MapUtils.getTrackColorBySpeed(average, averageToMaxSpeed, trackPoint);
                            polyline = addNewPolyline(trackColor);
                            if (endPos != null) {
                                polyline.addPoint(endPos);
                            } else if (startPos != null) {
                                polyline.addPoint(startPos);
                            }
                        } else {
                            if (polyline == null) {
                                polyline = addNewPolyline(trackColor);
                            }
                        }

                        endPos = trackPoint.getLatLong();
                        polyline.addPoint(endPos);
                        movementDirection.updatePos(endPos);

                        if (trackPoint.isPause() && showPauseMarkers) {
                            var marker = MapUtils.createPauseMarker(this, trackPoint.getLatLong());
                            waypointsLayer.addItem(marker);
                        }

                        if (!update) {
                            latLongs.add(endPos);
                        }

                        if (startPos == null) {
                            startPos = endPos;
                        }

                    }
                    trackpointsBySegments.debug().setTrackpointsDrawn(trackpointsBySegments.debug().getTrackpointsDrawn() + trackPoints.size());
                }
                trackPointsDebug.add(trackpointsBySegments.debug());
            } catch (SecurityException e) {
                Toast.makeText(MapsActivity.this, getString(R.string.error_reading_trackpoints, e.getMessage()), Toast.LENGTH_LONG).show();
                return;
            } catch (Exception e) {
                throw new RuntimeException("Error reading trackpoints", e);
            }

            Log.d(TAG, "Last trackpointId=" + lastTrackPointId);

            if (endPos != null) {
                setEndMarker(endPos);
            }

            GeoPoint myPos = null;
            if (update && endPos != null) {
                myPos = endPos;
                map.render();
            } else if (!latLongs.isEmpty()) {
                boundingBox = new BoundingBox(latLongs).extendMargin(1.2f);
                myPos = boundingBox.getCenterPoint();
            }

            if (myPos != null) {
                updateMapPositionAndRotation(myPos);
            }
            updateDebugTrackPoints();
        }

        ExecutorService myExecutor = Executors.newCachedThreadPool();
        myExecutor.execute(() -> {
            // get ski features
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("text/plain");
            String bbox = boundingBox.minLatitudeE6 / 1000000.0 + ","
                    + boundingBox.minLongitudeE6 / 1000000.0 + ","
                    + boundingBox.maxLatitudeE6 / 1000000.0 + ","
                    + boundingBox.maxLongitudeE6 / 1000000.0;

            String skiRouteRequestBodyData = "data=[out:json][timeout:90];" + "(way[\"piste:type\"](" +
                    bbox + ");relation[\"piste:type\"](" + bbox + ");" + ");" + "out geom;";

            // making API request for ski route data
            RequestBody body = RequestBody.create(mediaType, skiRouteRequestBodyData);
            Request request = new Request.Builder()
                    .url("https://overpass-api.de/api/interpreter")
                    .method("POST", body)
                    .addHeader("Content-Type", "text/plain")
                    .build();
            Response response = null;
            try {
                response = client.newCall(request).execute();
                JSONObject jsonResponse = new JSONObject(response.body().string());
                Log.println(Log.DEBUG, TAG, String.valueOf(jsonResponse));

                // reading elements array from JSON response
                JSONArray elements = jsonResponse.getJSONArray("elements");
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject element = elements.getJSONObject(i);
                    String type = element.getString("type");
                    long id = element.getLong("id");
                    JSONObject tags = element.getJSONObject("tags");
                    JSONArray nodes = element.getJSONArray("nodes"); // Getting the nodes array
                    JSONArray geometry = element.getJSONArray("geometry"); // coordinates
                    String name = tags.optString("name", "Unnamed");
                    Trail trail = Trail.getInstance(); // singleton class
                    SkiElements skiElements = SkiElements.parseJsonElement(element); // has ski-elements in the form of list
                    trail.addTrailData(skiElements); // adds ski-element list in the trails
                    // Now you can use these variables as needed
                    Log.i(TAG, "Type: " + type + ", ID: " + id + ", Name: " + name);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            // making API request for chair lift data
            String chairLiftRequestBodyData = "data=[out:json][timeout:90];" +
                    "(node[\"aerialway\"=\"chair_lift\"](" + bbox + ");" +
                    "way[\"aerialway\"=\"chair_lift\"](" + bbox + ");" +
                    "way[\"aerialway\"=\"chair_lift\"](" + bbox + ");" +
                    ");out geom;";
            body = RequestBody.create(mediaType, chairLiftRequestBodyData);
            request = new Request.Builder()
                    .url("https://overpass-api.de/api/interpreter")
                    .method("POST", body)
                    .addHeader("Content-Type", "text/plain")
                    .build();
            try {
                response = client.newCall(request).execute();
                JSONObject jsonResponse = new JSONObject(response.body().string());

                // extracting data
                JSONArray elements = jsonResponse.getJSONArray("elements");

                // integrate chair_lift tags into tracks
                ChairLift chairLift = ChairLift.getInstance(); // singleton class
                chairLift.clearData();
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject element = elements.getJSONObject(i);
                    String type = element.getString("type");
                    long id = element.getLong("id");
                    JSONObject tags = element.getJSONObject("tags");
                    JSONArray nodes = element.getJSONArray("nodes"); // Getting the nodes array
                    JSONArray geometry = element.getJSONArray("geometry"); // coordinates
                    String name = tags.optString("name", "Unnamed");

                    ChairLiftElements chairLiftElements = ChairLiftElements.parseJsonElement(element);
                    chairLift.addChairLiftData(chairLiftElements); // adds chairLift element list in the chairLifts

                    // Now you can use these variables as needed
                    Log.i(TAG, "Type: " + type + ", ID: " + id + ", Name: " + name);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void resetMapData() {
        unregisterContentObserver();

        tracksUri = null;
        trackPointsUri = null;
        waypointsUri = null;

        var layers = map.layers();

        // polylines
        if (polylinesLayer != null) {
            layers.remove(polylinesLayer);
        }
        if(polyline != null){
            map.layers().remove(polyline);
        }
        polylinesLayer = new GroupLayer(map);
        layers.add(polylinesLayer);

        // tracks
        lastTrackId = 0;
        lastTrackPointId = 0;
        colorCreator = new StyleColorCreator(StyleColorCreator.GOLDEN_RATIO_CONJUGATE / 2);
        trackColor = colorCreator.nextColor();
        polyline = null;
        startPos = null;
        endPos = null;
        endMarker = null;
        boundingBox = null;
        movementDirection = new MovementDirection();
        trackPointsDebug = new TrackPointsDebug();

        // waypoints
        if (waypointsLayer != null) {
            layers.remove(waypointsLayer);
        }
        waypointsLayer = createWaypointsLayer();
        map.layers().add(waypointsLayer);
        lastWaypointId = 0;
    }

    public void updateDebugTrackPoints() {
        if (PreferencesUtils.isDebugTrackPoints()) {
            binding.map.trackpointsDebugInfo.setText(
                    getString(R.string.debug_trackpoints_info,
                            trackPointsDebug.getTrackpointsReceived(),
                            trackPointsDebug.getTrackpointsInvalid(),
                            trackPointsDebug.getTrackpointsDrawn(),
                            trackPointsDebug.getTrackpointsPause(),
                            trackPointsDebug.getSegments(),
                            protocolVersion
                    ));
        } else {
            binding.map.trackpointsDebugInfo.setText("");
        }
    }

    private void setEndMarker(GeoPoint endPos) {
        synchronized (map.layers()) {
            if (endMarker != null) {
                endMarker.geoPoint = endPos;
                endMarker.setRotation(MapUtils.rotateWith(mapMode, movementDirection));
                waypointsLayer.populate();
                map.render();
            } else {
                endMarker = new MarkerItem(endPos.toString(), "", endPos);
                var symbol = MapUtils.createMarkerSymbol(this, R.drawable.ic_compass, false, MarkerSymbol.HotspotPlace.CENTER);
                endMarker.setMarker(symbol);
                endMarker.setRotation(MapUtils.rotateWith(mapMode, movementDirection));
                waypointsLayer.addItem(endMarker);
            }
        }
    }

    private PathLayer addNewPolyline(int trackColor) {
        float currentStrokeWidth = Math.max(strokeWidth, 4);;
        polyline = new PathLayer(map, trackColor, currentStrokeWidth);
        polylinesLayer.layers.add(polyline);
        return this.polyline;
    }

    private void readWaypoints(Uri data) {
        Log.i(TAG, "Loading waypoints from " + data);

        try {
            for (var waypoint : Waypoint.readWaypoints(getContentResolver(), data, lastWaypointId)) {
                lastWaypointId = waypoint.getId();
                final MarkerItem marker = MapUtils.createTappableMarker(this, waypoint);
                waypointsLayer.addItem(marker);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "No permission to read waypoints");
        } catch (Exception e) {
            Log.e(TAG, "Reading waypoints failed", e);
        }
    }

    private ItemizedLayer createWaypointsLayer() {
        var symbol = MapUtils.createPushpinSymbol(this);
        return new ItemizedLayer(map, new ArrayList<>(), symbol, this);
    }

    @Override
    public boolean onItemSingleTapUp(int index, MarkerInterface item) {
        MarkerItem markerItem = (MarkerItem) item;
        if (markerItem.uid != null) {
            var intent = new Intent("de.dennisguse.opentracks.MarkerDetails");
            intent.putExtra(EXTRA_MARKER_ID, (Long) markerItem.getUid());
            startActivity(intent);
        }
        return true;
    }

    @Override
    public boolean onItemLongPress(int index, MarkerInterface item) {
        return false;
    }

    private void readTracks(Uri data) {
        var tracks = Track.readTracks(getContentResolver(), data);
        this.storedTracksData = tracks;
        if (!tracks.isEmpty()) {
            var statistics = new TrackStatistics(tracks);
            removeStatisticElements();
            PreferencesUtils.getStatisticElements()
                    .stream()
                    .sorted(comparingInt(StatisticElement::ordinal))
                    .forEach(se -> addStatisticElement(se.getText(this, statistics)));
        }
    }

    private void removeStatisticElements() {
        var childsToRemove = new ArrayList<View>();
        for (int i = 0; i < binding.map.statisticsLayout.getChildCount(); i++) {
            var childView = binding.map.statisticsLayout.getChildAt(i);
            if (childView instanceof TextView) {
                childsToRemove.add(childView);
            }
        }
        childsToRemove.forEach((view -> {
            binding.map.statisticsLayout.removeView(view);
            binding.map.statistics.removeView(view);
        }));
    }

    private void addStatisticElement(String text) {
        var textView = new TextView(this);
        textView.setId(View.generateViewId());
        textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));
        textView.setTextColor(getColor(R.color.track_statistic));
        textView.setTextSize(COMPLEX_UNIT_PT, 10);
        binding.map.statisticsLayout.addView(textView);
        binding.map.statistics.addView(textView);
    }

    @Override
    public void onResume() {
        super.onResume();

        mapPreferences.load(map);
        binding.map.mapView.onResume();

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && boundingBox != null) {
            var mapPos = map.getMapPosition();
            mapPos.setByBoundingBox(boundingBox, map.getWidth(), map.getHeight());
            mapPos.setBearing(mapMode.getHeading(movementDirection));
            map.animator().animateTo(mapPos);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        int visibility = isInPictureInPictureMode ? View.GONE : View.VISIBLE;
        binding.toolbar.mapsToolbar.setVisibility(visibility);
        binding.map.fullscreenButton.setVisibility(visibility);
        binding.map.statistics.setVisibility(visibility);
    }

    private boolean isPiPMode() {
        return isInPictureInPictureMode();
    }

    @Override
    protected void onPause() {
        if (!isPiPMode()) {
            mapPreferences.save(map);
            binding.map.mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "register content observer");
        if (tracksUri != null && trackPointsUri != null && waypointsUri != null) {
            contentObserver = new OpenTracksContentObserver(tracksUri, trackPointsUri, waypointsUri, protocolVersion);
            try {
                getContentResolver().registerContentObserver(tracksUri, false, contentObserver);
                getContentResolver().registerContentObserver(trackPointsUri, false, contentObserver);
                if (waypointsUri != null) {
                    getContentResolver().registerContentObserver(waypointsUri, false, contentObserver);
                }
            } catch (SecurityException se) {
                Log.e(TAG, "Error on registering OpenTracksContentObserver", se);
                Toast.makeText(this, R.string.error_reg_content_observer, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStop() {
        unregisterContentObserver();
        super.onStop();
    }


    private void unregisterContentObserver() {
        if (contentObserver != null) {
            Log.d(TAG, "unregister content observer");
            getContentResolver().unregisterContentObserver(contentObserver);
            contentObserver = null;
        }
    }

    private void updateMapPositionAndRotation(final GeoPoint myPos) {
        var newPos = map.getMapPosition().setPosition(myPos).setBearing(mapMode.getHeading(movementDirection));
        map.animator().animateTo(newPos);
    }

}
