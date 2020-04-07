package com.jinsolins.maskmapsandroidtest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonSyntaxException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import noman.googleplaces.PlacesListener;
import noman.googleplaces.Place;
import noman.googleplaces.PlacesException;

public class MainActivity<ActivityMapCoronaBinding> extends AppCompatActivity
        implements OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleMap.OnCameraIdleListener,
        GoogleMap.OnCameraMoveStartedListener,
        GoogleMap.OnMarkerClickListener,
        PlacesListener {

    private int apiRequestCount;
    private GoogleMap mMap;
    private Marker currentMarker = null;

    private static final String TAG = "googlemap_example";
    private static final int GPS_ENABLE_REQUEST_CODE = 2001;
    private static final int UPDATE_INTERVAL_MS = 1000;  // 1초
    private static final int FASTEST_UPDATE_INTERVAL_MS = 500; // 0.5초

    private static final int PERMISSIONS_REQUEST_CODE = 100;
    boolean needRequest = false;

    String[] REQUIRED_PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

    Location mCurrentLocation;
    LatLng currentPosition;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest locationRequest;
    private Location location;

    private ArrayList<Marker> markerList = new ArrayList<>();
    public static ArrayList<CoronaApi.corona_item> corona_list = new ArrayList();

    public static boolean startFlagForCoronaApi;

    private ActivityMapCoronaBinding binding;
    private BottomSheetBehavior mBottomSheetBehavior;

    private View mLayout;   // Snackbar 사용하기 위한 View
    List<Marker> previous_maker = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mLayout = findViewById(R.id.layout_main);

        locationRequest = new LocationRequest()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL_MS)
                .setFastestInterval(FASTEST_UPDATE_INTERVAL_MS);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                                        .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(MainActivity.this);

    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        Log.d(TAG, "onMapReady :");

        mMap = googleMap;
        mMap.setOnCameraIdleListener(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnCameraMoveStartedListener(this);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(37.510759, 126.977943), 15));
        // 런타임 퍼미션 요청 대화상자나 GPS 활성 요청 대화상자가 보이기 전에 지도의 초기위치를 서울로 이동
        setDefaultLocation();

        // 런타임 퍼미션 처리
        // 1. 위치 퍼미션을 가지고 있는지 체크
        int hasFineLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);

        // 2. 이미 퍼미션을 가지고 있다면 위치 업데이트 시작
        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
            hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED) {

            startLocationUpdates();
        } else {
            // 3 -1. 사용자가 퍼미션을 거부한 적이 있는 경우, 요청 진행 전 사용자에게 접근 권한 여부 묻기
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])) {
                Snackbar.make(mLayout, "이 앱을 실행하려면 위치 접근 권한이 필요합니다.",
                        Snackbar.LENGTH_INDEFINITE).setAction("확인", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // 3 -2 사용자에게 퍼미션 요청 -> 요청 결과는 onRequestPermissionResult 에서 수신됨
                        ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
                    }
                }).show();
            } else {
                // 4 -1. 사용자가 퍼미션을 거부한 적이 없는 경우 퍼미션 요청 바로 수행 -> 요청 결과는 onRequestPermissionResult 에서 수신됨
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            }
        }

        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {

                Log.d(TAG, "onMapClick :");
            }
        });

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        // oneMarker();

        // 다중 마커
      /*  for (int i = 0; i < 10; i++) {
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(new LatLng(37.52487 + i, 126.92723))
                    .title("marker" + i);

            mMap.addMarker(markerOptions);
        }*/
    }

    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);

            List<Location> locationList = locationResult.getLocations();

            if (locationList.size() > 0) {
                location = locationList.get(locationList.size() -1);    // location = locationList.get(0);

                currentPosition = new LatLng(location.getLatitude(), location.getLongitude());

                String markerTitle = getCurrentAddress(currentPosition);
                String markerSnippet = "위도: " + String.valueOf(location.getLatitude())
                        + " 경도: " + String.valueOf(location.getLongitude());

                Log.d(TAG, "onLocationResult: " + markerSnippet);

                // 현재 위치에 마커 생성하고 이동
                setCurrentLocation(location, markerTitle, markerSnippet);
                mCurrentLocation = location;
            }
        }
    };

    private void startLocationUpdates() {
        if (!checkLocationServicesStatus()) {

            Log.d(TAG, "startLocationUpdates: call showDialogForLocationServiceSetting");
            showDialogForLocationServiceSetting();
        } else {
            int hasFineLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);

            if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED ||
                hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "startLocationUpdates: 퍼미션을 갖고 있지 않음");
                return;
            }

            Log.d(TAG, "startLocationUpdates: call mFusedLocationClient.requestLocationUpdates");

            mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

            if (checkPermission()) mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "onStart");

        if (checkPermission()) {

            Log.d(TAG, "onStart: call mFusedLocationClient.requestLocationUpdates");
            mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);

            if (mMap != null) mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mFusedLocationClient != null) {

            Log.d(TAG, "onStop: call stopLocationUpdates");
            mFusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    public String getCurrentAddress(LatLng latlng) {

        // GPS를 주소로 변환
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        List<Address> addresses;

        try {
            addresses = geocoder.getFromLocation(latlng.latitude, latlng.longitude, 1);
        } catch (IOException ioE) {
            Toast.makeText(this, "Geocoder 서비스 사용 불가", Toast.LENGTH_LONG).show();
            return "Geocoder 서비스 사용불가";
        } catch (IllegalArgumentException illegalArgumentE) {
            Toast.makeText(this, "잘못된 GPS 좌표", Toast.LENGTH_LONG).show();
            return "잘못된 GPS 좌표";
        }

        if (addresses == null || addresses.size() == 0) {
            Toast.makeText(this, "주소 미발견", Toast.LENGTH_LONG).show();
            return "주소 미발견";
        } else {
            Address address = addresses.get(0);
            return address.getAddressLine(0).toString();
        }
    }

    public boolean checkLocationServicesStatus() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public void setCurrentLocation(Location location, String markerTitle, String markerSnippet) {

        if (currentMarker != null) currentMarker.remove();

        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(currentLatLng)
                .title(markerTitle)
                .snippet(markerSnippet)
                .draggable(true);

        currentMarker = mMap.addMarker(markerOptions);

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(currentLatLng);
        mMap.moveCamera(cameraUpdate);
    }

    public void setDefaultLocation() {

        LatLng DEFAULT_LOCATION = new LatLng(37.56, 126.97);
        String markerTitle = "위치정보를 가져올 수 없음";
        String markerSnippet = "위치 퍼미션과 GPS 활성 여부를 확인하세요";

        if (currentMarker != null) currentMarker.remove();

        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(DEFAULT_LOCATION)
                .title(markerTitle)
                .snippet(markerSnippet)
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));

        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 15);
        mMap.moveCamera(cameraUpdate);

    }

    private boolean checkPermission() {

        int hasFineLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);

        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
            hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED) return true;

        return false;
    }

    // ActivityConpat.requestPermissions 를 사용한 퍼미션 요청의 결과를 리턴받는 메소드

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.length == REQUIRED_PERMISSIONS.length) {

            // 요청 코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신된 경우
            boolean check_result = true;

            // 모든 퍼미션을 허용했는지 체크
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    check_result = false;
                    break;
                }
            }

            if (check_result) {

                // 퍼미션을 허용한 경우 위치 업데이트 시작
                startLocationUpdates();
            } else {

                // 거부한 퍼미션이 있는 경우 앱을 사용할 수 없는 이유 안내 및 앱 종료
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])
                    || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[1])) {

                    // 사용자가 거부만 선택한 경우 앱을 다시 실행하여 허용을 선택하면 앱 사용 가능
                    Snackbar.make(mLayout, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.",
                            Snackbar.LENGTH_INDEFINITE).setAction("확인", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            finish();
                        }
                    }).show();
                } else {

                    // "다시 묻지 않음"을 사용자가 체크하고 거부를 선택한 경우에는 설정(앱 정보)에서 퍼미션을 허용해야 앱 사용 가능
                    Snackbar.make(mLayout, "퍼미션이 거부되었습니다. 설정(앱 정보)에서 퍼미션을 허용해야 합니다.",
                            Snackbar.LENGTH_INDEFINITE).setAction("확인", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            finish();
                        }
                    }).show();
                }
            }
        }
    }

    /**************************** GPS 활성화를 위한 메소드 ***************************/

    private void showDialogForLocationServiceSetting() {

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("위치 서비스 비활성화")
            .setMessage("앱을 사용하기 위해서는 위치 서비스가 필요합니다.\n" + "위치 설정을 수정하시겠습니까?")
            .setCancelable(true)
            .setPositiveButton("설정", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Intent callGPPSSettingIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(callGPPSSettingIntent, GPS_ENABLE_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {

        case GPS_ENABLE_REQUEST_CODE:

            if (checkLocationServicesStatus()) {
                if (checkPermission()) {

                    Log.d(TAG, "onActivityResult: GPS 활성화 되있음");
                    needRequest = true;

                    return;
                }
            }
            break;
        }
    }

   /* // 서울 마커 클릭 리스너
    public void oneMarker() {

        LatLng SEOUL = new LatLng(37.56, 126.97);

        mMap.addMarker(new MarkerOptions().position(SEOUL).title("서울특별시").snippet("대한민국 수도"));

        mMap.moveCamera(CameraUpdateFactory.newLatLng(SEOUL));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(37.52487, 126.92723)));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(10));
    }*/

    @Override
    public void onCameraIdle() {
        removeMarkerAll();

        String lat = String.valueOf(mMap.getCameraPosition().target.latitude);
        String lon = String.valueOf(mMap.getCameraPosition().target.longitude);
        startFlagForCoronaApi = true;

        new CoronaApi().execute(lat, lon, "");

        apiRequestCount = 0;
        final Handler temp_handler = new Handler();
        temp_handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (apiRequestCount < 100) {
                    if (startFlagForCoronaApi) {
                        apiRequestCount ++;
                        temp_handler.postDelayed(this, 100);
                    } else {
                        // api 호출이 완료되었을 때
                        drawMarker();
                    }
                } else {
                    // api 호출이 10초 이상 경과되었을 때
                    Toast.makeText(getApplicationContext(), "호출에 실패하였습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show();
                }
            }
        }, 100);
    }

    private void removeMarkerAll() {
        for (Marker marker : markerList) {
            marker.remove();
        }
    }

    private void drawMarker() {
        for (int i = 0; i < corona_list.size(); i++) {
            CoronaApi.corona_item item = corona_list.get(i);
            Log.d("코로나 로그", String.valueOf(item));
            String remain_stat = item.getRemain_stat();

            switch (remain_stat) {
                case "plenty" : {
                    remain_stat = "100개 이상";
                    break;
                }
                case "some" : {
                    remain_stat = "30개 이상 100개 미만";
                    break;
                }
                case "few" : {
                    remain_stat = "2개 이상 30개 미만";
                    break;
                }
                case "empty" : {
                    remain_stat = "1개 이하";
                    break;
                }
            }

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(Double.parseDouble(item.getLat()), Double.parseDouble(item.getLng())))
                    .title(item.getName())
                    .snippet(item.getAddr() + "@" + item.getCreated_at() + "@" + item.getRemain_stat() + "@" + item.getStock_at() + "@" + item.getType())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    .alpha(0.8f));
            markerList.add(marker);
        }
        return;
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        Log.d("onMarkerClick", "click");

        String addr= marker.getSnippet().split("@")[0];
        String created_at= marker.getSnippet().split("@")[1];
        String remain_stat= marker.getSnippet().split("@")[2];
        String stock_at= marker.getSnippet().split("@")[3];
        String type= marker.getSnippet().split("@")[4];

        switch (type) {
            case "01" :{
                type = "약국";
                break;
            }
            case "02" :{
                type = "우체국";
                break;
            }
            case "03" :{
                type = "농협";
                break;
            }
        }

        switch (remain_stat) {
            case "plenty" : {
                remain_stat = "100개이상";
                break;
            }
            case "some" : {
                remain_stat = "30개 이상 100개 미만";
                break;
            }
            case "few" : {
                remain_stat = "2개 이상 30개 미만";
                break;
            }
            case "empty" : {
                remain_stat = "1개 이하";
                break;
            }
        }
        return true;
    }

    @Override
    public void onCameraMoveStarted(int i) {
       // mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    @Override
    public void onPlacesFailure(PlacesException e) {

    }

    @Override
    public void onPlacesStart() {

    }

    @Override
    public void onPlacesSuccess(final List<Place> places) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (noman.googleplaces.Place place : places) {

                    LatLng latLng = new LatLng(place.getLatitude(), place.getLongitude());

                    String markerSnippet = getCurrentAddress(latLng);

                    MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(latLng)
                            .title(place.getName())
                            .snippet(markerSnippet);
                            Marker item = mMap.addMarker(markerOptions);
                            previous_maker.add(item);
                }
                // 중복 마커 제거
                HashSet<Marker> hashSet = new HashSet<>();
                hashSet.addAll(previous_maker);
                previous_maker.clear();
                previous_maker.addAll(hashSet);

            }
        });
    }

    @Override
    public void onPlacesFinished() {

    }


    /*********************** CoronaApi ***********************/
    public static class CoronaApi extends AsyncTask<String, String, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }

        @Override
        protected String doInBackground(String... strings) {
            Log.d("Task3", "POST");
            String temp = "Not Gained";
            try {
                temp = GET(strings[0], strings[1]);
                Log.d("REST", temp);
                return temp;
            } catch (IOException ioE) {
                ioE.printStackTrace();
            }
            return temp;
        }

        private String GET(String x, String y) throws IOException {
            String corona_API = "https://8oi9s0nnth.apigw.ntruss.com/corona19-masks/v1/storesByGeo/json?lat=" + x + "&lng=" + y + "&m=1000";

            String data = "";
            String myUrl3 = String.format(corona_API, x);

            try {
                URL url = new URL(myUrl3);
                Log.d("CoronaApi", "The response is: " + url);
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();

                String line;
                String result = "";

                BufferedReader bf;
                bf = new BufferedReader(new InputStreamReader(url.openStream()));

                while ((line = bf.readLine()) != null) {
                    result = result.concat(line);
                }
                Log.d("CoronaApi", "The response is: " + result);
                JSONObject root = new JSONObject(result);

                JSONArray coronaArray = root.getJSONArray("stores");
                for (int i = 0; i < coronaArray.length(); i++) {
                    JSONObject item = coronaArray.getJSONObject(i);
                    Log.d("corona", item.getString("name"));

                    corona_item corona_item = new corona_item(
                            item.getString("lat"),
                            item.getString("lng"),
                            item.getString("addr"),
                            item.getString("code"),
                            item.getString("created_at"),
                            item.getString("name"),
                            item.getString("remain_stat"),
                            item.getString("stock_at"),
                            item.getString("type")
                    );
                    MainActivity.corona_list.add(corona_item);
                }
                startFlagForCoronaApi = false;

            } catch (JSONException |JsonSyntaxException | NullPointerException e) {
                e.printStackTrace();
            }
            return data;
        }

        public class corona_item {
            private String addr;
            private String code;
            private String created_at;
            private String lat;
            private String lng;
            private String name;
            private String remain_stat;
            private String stock_at;
            private String type;

            public corona_item(String lat, String lng, String addr, String code, String created_at, String name,
                               String remain_stat, String stock_at, String type) {
                this.lat = lat;
                this.lng = lng;
                this.addr = addr;
                this.code = code;
                this.created_at = created_at;
                this.name = name;
                this.remain_stat = remain_stat;
                this.stock_at = stock_at;
                this.type = type;
            }

            public String getAddr() {
                return addr;
            }

            public String getCode() {
                return code;
            }

            public String getCreated_at() {
                return created_at;
            }

            public String getLat() {
                return lat;
            }

            public String getLng() {
                return lng;
            }

            public String getName() {
                return name;
            }

            public String getRemain_stat() {
                return remain_stat;
            }

            public String getStock_at() {
                return stock_at;
            }

            public String getType() {
                return type;
            }

            public void setAddr(String addr) {
                this.addr = addr;
            }

            public void setCode(String code) {
                this.code = code;
            }

            public void setCreated_at(String created_at) {
                this.created_at = created_at;
            }

            public void setLat(String lat) {
                this.lat = lat;
            }

            public void setLng(String lng) {
                this.lng = lng;
            }

            public void setName(String name) {
                this.name = name;
            }

            public void setRemain_stat(String remain_stat) {
                this.remain_stat = remain_stat;
            }

            public void setStock_at(String stock_at) {
                this.stock_at = stock_at;
            }

            public void setType(String type) {
                this.type = type;
            }
        }
    }
}
