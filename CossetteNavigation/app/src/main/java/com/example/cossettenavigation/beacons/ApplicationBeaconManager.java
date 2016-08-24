package com.example.cossettenavigation.beacons;

import android.app.Application;
import android.util.Log;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.EstimoteSDK;
import com.estimote.sdk.Region;
import com.example.cossettenavigation.Utilities;
import com.example.cossettenavigation.map.Floor;
import com.example.cossettenavigation.map.Map;
import com.example.cossettenavigation.map.Point2D;
import com.example.cossettenavigation.map.Zone;
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Global application state used to detect and manage beacons.
 *
 * Monitoring is coarse, sending enter and exit events (30 second intervals).
 * Ranging is fine, providing power and approximate distance readings (1 second intervals).
 *
 * Created by Bruno on 2016-07-22.
 *
 * @see <a href="http://developer.estimote.com/android/tutorial/part-2-background-monitoring/">Monitoring tutorial</a>
 * @see <a href="http://developer.estimote.com/android/tutorial/part-3-ranging-beacons/">Ranging tutorial</a>
 */
public class ApplicationBeaconManager extends Application {

    private final String TAG = "AppBeaconManager";

    private final Region ALL_BEACONS_REGION = new Region("All Beacons", null, null, null);

    /**
     * The maximum distance a beacon can be from the device while being used in the position
     * trilateration algorithm (in metres).
     */
    private static double MAX_BEACON_DISTANCE_FOR_TRILATERATION = 5;
    private static double BEACON_RANGE_FOR_NEARBY_ZONE = 10;

    private BeaconManager beaconManager;

    /**
     * Set of beacons to be tracked over time (for location algorithms).
     */
    private HashMap<Region, BeaconTrackingData> trackedBeacons = new HashMap<>();




    @Override
    public void onCreate() {
        //Log.v(TAG, "onCreate()");

        super.onCreate();

        // Initialize Map class
        Map map = new Map();

        // App ID & App Token can be taken from App section of Estimote Cloud.
        //EstimoteSDK.initialize(this, getString(R.string.app_name), getString(R.string.app_name));
        // Optional, debug logging.
        EstimoteSDK.enableDebugLogging(true);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                //logTrackedBeacons();
                //Log.v(TAG, getTrackedBeaconsDescription());

                //getEstimatedLocation();
            }
        }, 1, 1000);

        beaconManager = new BeaconManager(this);
        //setNormalForegroundScan();
        setResponsiveForegroundScan();

        // Callback when the beacon manager has connected to the beacon service
        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                Log.v(TAG, "BeaconManager.ServiceReadyCallback.onServiceReady()");

                setMonitoringListener();
                setRangingListener();

                startScanning();
            }
        });
    }

    private void setNormalForegroundScan() {
        beaconManager.setForegroundScanPeriod(1000, 0);
    }

    private void setResponsiveForegroundScan() {
        beaconManager.setForegroundScanPeriod(250, 0);
    }




    private void setMonitoringListener() {
        beaconManager.setMonitoringListener(new BeaconManager.MonitoringListener() {
            @Override
            public void onEnteredRegion(Region region, List<Beacon> list) {
                Log.v(TAG, "BeaconManager.MonitoringListener.onEnteredRegion()");
                Log.v(TAG, region.toString());
                for (Beacon beacon : list) {
                    Log.v(TAG, beacon.toString());
                }

                if (list.size() > 0) {
                    updateTrackedBeacon(region, list.get(0));
                } else {
                    //Log.v(TAG, "setMonitoringListener(): No beacons in region");
                }
            }

            @Override
            public void onExitedRegion(Region region) {
                Log.v(TAG, "BeaconManager.MonitoringListener.onExitedRegion()");
                Log.v(TAG, region.toString());

                removeTrackedBeacon(region);
            }
        });
    }


    private void setRangingListener() {
        beaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> list) {
                //Log.v(TAG, "BeaconManager.RangingListener.onBeaconsDiscovered()");

/*                Log.v(TAG, region.toString());
                for (Beacon beacon : list) {
                    Log.v(TAG, beacon.toString());
                }*/

                if (list.size() > 0) {
                    updateTrackedBeacon(region, list.get(0));
                } else {
                    //Log.v(TAG, "setRangingListener(): No beacons in region");
                }
            }
        });
    }




    private void startScanning() {
        // Monitor all beacons
        for (com.example.cossettenavigation.map.Beacon beacon : Map.getAllBeacons()) {
            Region region = new Region(
                    beacon.getName(),
                    beacon.getUUID(),
                    beacon.getMajor(),
                    beacon.getMinor());
            beaconManager.startMonitoring(region);
            beaconManager.startRanging(region);
        }
    }




    private void updateTrackedBeacon(Region region, Beacon beacon) {
        //Log.v(TAG, "updateTrackedBeacon()");

/*        Log.v(TAG, String.format(
        "Beacon: accuracy = %f, proximity = %s, %s",
        Utils.computeAccuracy(beacon), Utils.computeProximity(beacon), beacon));*/

        // Add the beacon if it isn't already tracked
        if (!trackedBeacons.containsKey(region)) {

            // Try to find the beacon in the map
            com.example.cossettenavigation.map.Beacon mapBeacon = null;

            for (com.example.cossettenavigation.map.Beacon testBeacon : Map.getAllBeacons()) {
                if (Utilities.areEqual(region, testBeacon)) {
                    mapBeacon = testBeacon;
                }
            }

            if (mapBeacon == null) {
                Log.e(TAG, String.format(
                        "updateTrackedBeacon(): Tracked beacon not found in map\nregion = %s\nbeacon = %s",
                        region, beacon));
                return;
            } else {
                trackedBeacons.put(region, new BeaconTrackingData(mapBeacon));
            }
        }

        // The beacon must be in the tracked set, so update it with measurements
        trackedBeacons.get(region).addMeasurements(beacon);

        //Log.v(TAG, trackedBeacons.get(region).toString());
    }

    private void removeTrackedBeacon(Region region) {
        trackedBeacons.remove(region);
    }

    public BeaconTrackingData getBeaconTrackingData(com.example.cossettenavigation.map.Beacon beacon) {
        return trackedBeacons.get(new Region(
                beacon.getName(),
                beacon.getUUID(),
                beacon.getMajor(),
                beacon.getMinor()));
    }

    public com.example.cossettenavigation.map.Beacon getNearestBeacon() {
        if (trackedBeacons.size() > 0) {
            double minAccuracy = Double.POSITIVE_INFINITY;
            com.example.cossettenavigation.map.Beacon minBeacon = null;

            for (HashMap.Entry<Region, BeaconTrackingData> trackedBeacon : trackedBeacons.entrySet()) {
                if (trackedBeacon.getValue().getEstimatedAccuracy() < minAccuracy) {
                    minAccuracy = trackedBeacon.getValue().getEstimatedAccuracy();
                    minBeacon = trackedBeacon.getValue().getBeacon();
                }
            }

            return minBeacon;
        }

        else {
            return null;
        }
    }

    public ArrayList<Zone> getNearbyZones() {
        ArrayList<Zone> nearbyZones = new ArrayList<>();

        for (HashMap.Entry<Region, BeaconTrackingData> trackedBeacon : trackedBeacons.entrySet()) {
            if (trackedBeacon.getValue().getEstimatedAccuracy() <= BEACON_RANGE_FOR_NEARBY_ZONE) {
                ArrayList<Zone> zones = trackedBeacon.getValue().getBeacon().getZones();

                for (Zone zone : zones) {
                    if (!nearbyZones.contains(zone) && zone.getIsDestination()) {
                        nearbyZones.add(zone);
                    }
                }
            }
        }

        return nearbyZones;
    }



    /**
     * @see <a href="https://github.com/lemmingapex/Trilateration">Trilateration example</a>
     * @return Estimated location (on map grid), or null if not found
     */
    public Point2D getEstimatedLocation() {
        // Get beacon positions and distances
        // Convert positions to metres
        // { { x, y }, { x, y }, ... }
        ArrayList<double[]> positions = new ArrayList<>();
        ArrayList<Double> distances = new ArrayList<>();

        // Loop through tracked beacons
        for (HashMap.Entry<Region, BeaconTrackingData> trackedBeacon : trackedBeacons.entrySet()) {
            if (trackedBeacon.getValue().getEstimatedAccuracy() <= MAX_BEACON_DISTANCE_FOR_TRILATERATION) {
                // Add position and distance (in metres)
                positions.add(new double[] {
                        trackedBeacon.getValue().getBeacon().getXPosition() * Map.metresPerGridUnit,
                        trackedBeacon.getValue().getBeacon().getYPosition() * Map.metresPerGridUnit });
                distances.add(trackedBeacon.getValue().getEstimatedAccuracy());
            }
        }


        // Trilaterate position

        // If there are 3 or more beacons (required for 2D triangulation)
        if (positions.size() >= 3) {
/*            double[][] positions = new double[][] { { 5.0, -6.0 }, { 13.0, -15.0 }, { 21.0, -3.0 }, { 12.4, -21.2 } };
            double[] distances = new double[] { 8.06, 13.97, 23.32, 15.31 };*/

            NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(
                    new TrilaterationFunction(
                            Utilities.getDoubleDoubleArray(positions), Utilities.getDoubleArray(distances)),
                    new LevenbergMarquardtOptimizer());
            LeastSquaresOptimizer.Optimum optimum = solver.solve();

            // the answer
            double[] centroid = optimum.getPoint().toArray();

            // error and geometry information; may throw SingularMatrixException depending the threshold argument provided
            /*RealVector standardDeviation = optimum.getSigma(0);
            RealMatrix covarianceMatrix = optimum.getCovariances(0);*/

            Point2D estimatedLocation = new Point2D(centroid[0] / Map.metresPerGridUnit,
                                                    centroid[1] / Map.metresPerGridUnit);

            //Log.i(TAG, "getEstimatedLocation(): " + estimatedLocation);

            return estimatedLocation;

        } else {
/*            Log.i(TAG, String.format(
                    "getEstimatedLocation(): Not enough beacons within %.1fm to trilaterate location",
                    MAX_BEACON_DISTANCE_FOR_TRILATERATION));*/

            return null;
        }
    }

    /**
     * @return Estimated floor or null.
     */
    public Floor getEstimatedFloor() {
        com.example.cossettenavigation.map.Beacon nearestBeacon = getNearestBeacon();
        if (nearestBeacon != null) {
            return nearestBeacon.getFloor();
        }

        return null;
    }



    public void logTrackedBeacons() {
        String string = "logTrackedBeacons():\n";

        for (java.util.Map.Entry<Region, BeaconTrackingData> beacon : trackedBeacons.entrySet()) {
            string += String.format(
                    "%s : %s\n",
                    beacon.getValue(), beacon.getKey());
        }

        Log.v(TAG, string);
    }

    public String getTrackedBeaconsDescription() {
        //Log.v(TAG, "getTrackedBeaconsDescription()");

        String string = "";

        for (java.util.Map.Entry<Region, BeaconTrackingData> entry : trackedBeacons.entrySet()) {
            string += String.format(
                    "%s : %.2f m\n",
                    entry.getKey().getIdentifier(), entry.getValue().getEstimatedAccuracy());
        }

        Point2D estimatedLocation = getEstimatedLocation();
        if (estimatedLocation == null) {
            string += "Location Unavailable";
        } else {
            string += String.format(
                    "(%.1f, %.1f)",
                    estimatedLocation.x, estimatedLocation.y);
        }

        return string;
    }


}
