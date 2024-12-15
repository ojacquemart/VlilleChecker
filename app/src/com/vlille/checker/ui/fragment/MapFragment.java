package com.vlille.checker.ui.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.vlille.checker.R;
import com.vlille.checker.db.StationEntityManager;
import com.vlille.checker.model.Station;
import com.vlille.checker.ui.HomeActivity;
import com.vlille.checker.ui.delegate.StationUpdateDelegate;
import com.vlille.checker.ui.osm.MapState;
import com.vlille.checker.ui.osm.MapView;
import com.vlille.checker.utils.ToastUtils;

import org.droidparts.annotation.inject.InjectDependency;
import org.droidparts.fragment.support.v4.Fragment;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * A fragment to localize and bookmark stations from a map, using OpenStreetMap.
 */
public class MapFragment extends Fragment implements StationUpdateDelegate {

    private static final String TAG = MapFragment.class.getSimpleName();

    private static final int REQUEST_CODE_LOCATION_AND_STORAGE = 42;
    private static final String[] PERMISSIONS_LOCATION_AND_STORAGE = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @InjectDependency
    private StationEntityManager stationEntityManager;
    private MapState state = new MapState();

    private MapView mapView;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Log.d(TAG, "onCreate");

        if (!this.state.isInitialized()) {
            this.state.save(MapView.DEFAULT_CENTER_GEO_POINT, MapView.DEFAULT_ZOOM_LEVEL);
        }
    }

    public void setCenter(GeoPoint center) {
        Log.d(TAG, "setCenter " + center);
        this.state.currentCenter = center;
        this.state.zoomLevel = MapView.DEFAULT_ZOOM_LEVEL;
    }

    @Override
    public View onCreateView(Bundle savedInstanceState,
                             LayoutInflater inflater, ViewGroup container) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreateView");

        final View view = inflater.inflate(R.layout.maps, container, false);
        mapView = (MapView) view.findViewById(R.id.mapview);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        checkPermissions();

        List<Station> stations = stationEntityManager.findAll();

        mapView.setMapInfos(state, stations);
        mapView.setHomeActivity((HomeActivity) getActivity());
        mapView.setStationUpdateDelegate(this);
        mapView.init();

        addLocationEnablerClickListener(getView());
    }

    @Override
    public void update(Station station) {
        stationEntityManager.update(station);
    }

    private void addLocationEnablerClickListener(final View view) {
        final FloatingActionButton locationEnabler = (FloatingActionButton) view.findViewById(R.id.maps_location_enable);
        locationEnabler.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (isPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    toastLocationNotGrantedPermissionMessage();
                } else {
                    mapView.updateLocationCircle();
                    locationEnabler.setImageResource(mapView.isLocationOn() ? R.drawable.ic_location_on : R.drawable.ic_location_off);
                }
            }
        });
    }

    private void checkPermissions() {
        Log.d(TAG, "checkPermissions");

        requestPermissionsIfNeeded();
    }

    private void requestPermissionsIfNeeded() {
        List<String> deniedPermissions = getDeniedPermissions();
        if (!deniedPermissions.isEmpty()) {
            requestPermissions(PERMISSIONS_LOCATION_AND_STORAGE, REQUEST_CODE_LOCATION_AND_STORAGE);
        }
    }

    private List<String> getDeniedPermissions() {
        List<String> deniedPermissions = new ArrayList<>();

        for (String permission : PERMISSIONS_LOCATION_AND_STORAGE) {
            if (isPermissionDenied(permission)) {
                deniedPermissions.add(permission);
            }
        }

        return deniedPermissions;
    }

    private boolean isPermissionDenied(String permission) {
        return ContextCompat.checkSelfPermission(getContext(), permission) == PackageManager.PERMISSION_DENIED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            toastLocationNotGrantedPermissionMessage();
        }

        // we ignore the WRITE_EXTERNAL_STORAGE since it seems to work fine without it
        // there was a rework in the later Android versions about the storage handling
        // but we will handle that later (or not)
    }

    private void toastLocationNotGrantedPermissionMessage() {
        ToastUtils.show(getActivity(), getString(R.string.permission_location_not_granted));
    }

    @Override
    public void onResume() {
        super.onResume();

        mapView.updateStations();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        GeoPoint mapCenter = (GeoPoint) mapView.getMapCenter();
        if (mapView.isLocationOn()) {
            mapCenter = null;
        }
        state.save(mapCenter, mapView.getZoomLevel());
    }

}
