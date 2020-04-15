package com.jinsolins.maskmapsandroidtest;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.jinsolins.maskmapsandroidtest.api.ApiClient;
import com.jinsolins.maskmapsandroidtest.model.StoreResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity<ActivityMapCoronaBinding> extends AppCompatActivity
        implements OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback,
        GoogleMap.OnCameraIdleListener,
        GoogleMap.OnCameraMoveStartedListener,
        GoogleMap.OnMarkerClickListener,
        View.OnClickListener {

    private int apiRequestCount;
    private GoogleMap mMap;
    private Geocoder geocoder;
    private Marker currentMarker = null;
    private ImageButton searchBtn;
    private EditText searchBox;

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
    public static ArrayList<StoreResponse.Store> corona_list = new ArrayList();

    private ActivityMapCoronaBinding binding;
    private BottomSheetBehavior mBottomSheetBehavior;

    private View mLayout;   // Snackbar 사용하기 위한 View

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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

        // 내 위치 반경 nkm 검색 버튼
        Button button1km = findViewById(R.id.button_1km);
        Button button2km = findViewById(R.id.button_2km);
        Button button3km = findViewById(R.id.button_3km);
        button1km.setOnClickListener(MainActivity.this);
        button2km.setOnClickListener(MainActivity.this);
        button3km.setOnClickListener(MainActivity.this);

        // 주소 검색 엔터키 이벤트
        searchBtn = findViewById(R.id.search_icon_btn);
        searchBox = findViewById(R.id.editText);

        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

                switch (actionId) {
                    case EditorInfo.IME_ACTION_SEARCH:
                        searchBtn.performClick();
                        break;
                    default:
                        // 기본 엔터키 동작
                        return false;
                }
                return true;
            }
        });

        searchBox.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        // 주소 검색 이벤트
        searchBtn.setOnClickListener(new ImageButton.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (markerList != null) markerList.clear();

                String addressStr = searchBox.getText().toString().trim();

                List<Address> addressList = null;

                try {
                    addressList = geocoder.getFromLocationName(
                            addressStr,
                            20); // 최대 검색 결과 개수
                } catch (IOException | IndexOutOfBoundsException e) {
                    e.printStackTrace();
                }

                stopLocationUpdates();

                if (addressStr.length() == 0) {
                    Toast.makeText(MainActivity.this, "목적지를 입력하세요.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (addressList != null && addressList.size() == 0) {
                    Toast.makeText(MainActivity.this, "조회된 결과가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] splitStr = addressList.get(0).toString().split(",");
                String address = splitStr[0].substring(splitStr[0].indexOf("\"") + 1, splitStr[0].length() - 2); // 주소
                String latitude = splitStr[10].substring(splitStr[10].indexOf("=") + 1); // 위도
                String longitude = splitStr[12].substring(splitStr[12].indexOf("=") + 1); // 경도

                location.setLatitude(Double.parseDouble(latitude));
                location.setLongitude(Double.parseDouble(longitude));

                // 좌표(위도, 경도) 생성
                currentPosition = new LatLng(location.getLatitude(), location.getLongitude());

                String markerTitle = getCurrentAddress(currentPosition);
                String markerSnippet = "위도: " + latitude
                        + " 경도: " + longitude;

                // 해당 좌표로 화면 이동
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 15));

                setCurrentLocation(location, markerTitle, markerSnippet);
                mCurrentLocation = location;

                drawMaskMarkers(1);
            }
        });
    }

    // 반경 거리 버튼 클릭 이벤트
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_1km:
                drawMaskMarkers(1);
                break;
            case R.id.button_2km:
                drawMaskMarkers(2);
                break;
            case R.id.button_3km:
                drawMaskMarkers(3);
                break;
        }
    }

    // 반경 거리에 따른 마커 생성
    private void drawMaskMarkers(int type) {

        float zoomLevel = type == 1 ? 14.5f : type == 2 ? 14f : 13.5f;
        int km = type * 1000;

        if (markerList != null) {
            removeMarkerAll();
            markerList.clear();
        }

        mMap.animateCamera(CameraUpdateFactory.zoomTo(zoomLevel));
        getMaskInfo(km);
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        Log.d(TAG, "onMapReady :");

        mMap = googleMap;
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                currentPosition = latLng;
                drawMaskMarkers(1);

                if (currentMarker != null) currentMarker.remove();

                currentMarker = mMap.addMarker(new MarkerOptions().position(currentPosition)
                        .title("")
                        .snippet(""));

                stopLocationUpdates();
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 15));
            }
        });

        mMap.setOnCameraIdleListener(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnCameraMoveStartedListener(this);

        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
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

  //      mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
    }

    // 현재 위치 표시
    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);

            List<Location> locationList = locationResult.getLocations();

            if (locationList.size() > 0) {
                location = locationList.get(locationList.size() - 1);    // location = locationList.get(0);

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
    protected void onPause() {
        super.onPause();
        mFusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void stopLocationUpdates() {
        mFusedLocationClient.removeLocationUpdates(locationCallback);
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
        geocoder = new Geocoder(this, Locale.getDefault());

        List<Address> addresses;

        try {
            addresses = geocoder.getFromLocation(latlng.latitude, latlng.longitude, 1);
        } catch (IOException ioE) {
            Toast.makeText(this, "Geocoder 서비스 사용불가", Toast.LENGTH_LONG).show();
            return "Geocoder 서비스 사용불가";
        } catch (IllegalArgumentException illegalArgumentE) {
            Toast.makeText(this, "잘못된 GPS 좌표", Toast.LENGTH_LONG).show();
            return "잘못된 GPS 좌표";
        }

        if (addresses == null || addresses.size() == 0) {
            Toast.makeText(this, "조회된 주소가 없습니다", Toast.LENGTH_LONG).show();
            return "조회된 주소가 없습니다";
        } else {
            Address address = addresses.get(0);
            return address.getAddressLine(0);
        }
    }

    public boolean checkLocationServicesStatus() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public void setCurrentLocation(Location location, String markerTitle, String markerSnippet) {

        if (currentMarker != null) currentMarker.remove();

        mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
    }

    // 기본 위치 설정
    public void setDefaultLocation() {

        LatLng DEFAULT_LOCATION = new LatLng(37.56, 126.97);
        String markerTitle = "위치정보를 가져올 수 없음";
        String markerSnippet = "위치 퍼미션과 GPS 활성 여부를 확인하세요";

        if (currentMarker != null) currentMarker.remove();

        mMap.addMarker(new MarkerOptions().position(DEFAULT_LOCATION)
                .title(markerTitle)
                .snippet(markerSnippet));

        Toast.makeText(this, "서욽특별시", Toast.LENGTH_LONG);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 15));
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
                    Snackbar.make(mLayout, "권한이 거부되었습니다. 앱을 다시 실행하여 권한을 허용해주세요.",
                            Snackbar.LENGTH_INDEFINITE).setAction("확인", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            finish();
                        }
                    }).show();
                } else {
                    // "다시 묻지 않음"을 사용자가 체크하고 거부를 선택한 경우에는 설정(앱 정보)에서 퍼미션을 허용해야 앱 사용 가능
                    Snackbar.make(mLayout, "권한이 거부되었습니다. 설정(앱 정보)에서 권한을 허용해야 합니다.",
                            Snackbar.LENGTH_INDEFINITE).setAction("확인", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {

                            finish();
                        }
                    }).show();
                }
            }
        }
        stopLocationUpdates();
    }

    /**************************** GPS 활성화를 위한 메소드 ***************************/
    private void showDialogForLocationServiceSetting() {

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("위치 서비스 활성화 동의")
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

                        Log.d(TAG, "onActivityResult: GPS 활성화 상태");
                        needRequest = true;

                        return;
                    }
                }
                break;
        }
    }

    @Override
    public void onCameraIdle() {
    }

    private void removeMarkerAll() {
        for (Marker marker : markerList) {
            marker.remove();
        }
    }

    // Retrofit 을 사용한 마스크 정보 표시
    private void getMaskInfo(int m) {

        ApiClient.getApiService().test(currentPosition.latitude, currentPosition.longitude, m).enqueue(new Callback<StoreResponse>() {
            @Override
            public void onResponse(Call<StoreResponse> call, Response<StoreResponse> response) {
                corona_list = response.body().getStores();
                drawMarker();
            }

            @Override
            public void onFailure(Call<StoreResponse> call, Throwable t) {
                Log.e("ERR", t.getMessage());
            }
        });
        apiRequestCount = 0;
    }

    // 마커 생성
    private void drawMarker() {
        if (corona_list == null) {
            Toast.makeText(this, "데이터가 존재하지 않습니다", Toast.LENGTH_LONG).show();
            return;
        }

        for (int i = 0; i < corona_list.size(); i++) {
            StoreResponse.Store item = corona_list.get(i);

            Log.i("TEST TAG", item.toString());

            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(item.getLat(), item.getLng()))
                    .title(item.getName())
                    .snippet(item.getAddress() + "@" + item.getCreatedAt() + "@" + item.getRemainStat() + "@" + item.getStockAt() + "@" + item.getType()));

            if (item.getRemainStat() == null || item.getRemainStat().equals("empty") || item.getRemainStat().equals("break")) {
                marker.setIcon(newMaskMarker(R.drawable.marker_gray2));
            } else if (item.getRemainStat().equals("plenty")) {
                marker.setIcon(newMaskMarker(R.drawable.marker_green));
            } else if (item.getRemainStat().equals("some")) {
                marker.setIcon(newMaskMarker(R.drawable.marker_orange));
            } else if (item.getRemainStat().equals("few")) {
                marker.setIcon(newMaskMarker(R.drawable.marker_red));
            }

            markerList.add(marker);
        }
        return;
    }

    // 마커 이미지 변경
    public BitmapDescriptor newMaskMarker(int img) {
        BitmapDrawable bitmapDrawable = (BitmapDrawable) getResources().getDrawable(img);
        Bitmap newMarker = Bitmap.createScaledBitmap(bitmapDrawable.getBitmap(), 85, 85, false);

        return BitmapDescriptorFactory.fromBitmap(newMarker);
    }

    // 마커 클릭 이벤트 및 알림창 생성
     @Override
    public boolean onMarkerClick(Marker marker) {
        Log.d("onMarkerClick", "click");

        String[] maskInfo = marker.getSnippet().split("@");
        String addr = maskInfo[0];
        String created_at = maskInfo[1];
        String remain_stat = maskInfo[2];
        String stock_at = maskInfo[3];
        String type = maskInfo[4];
        String name = marker.getTitle();

        switch (remain_stat) {
            case "plenty": {
                remain_stat = "100개 이상";
                break;
            }
            case "some": {
                remain_stat = "30개 ~ 99개";
                break;
            }
            case "few": {
                remain_stat = "2개 ~ 29개";
                break;
            }
            case "empty": {
                remain_stat = "0 ~ 1개";
                break;
            }
            case "break": {
                remain_stat = "판매 중지";
            }
        }

        String massage = "재고 상태: " + remain_stat
                + "\n\n" + addr
                + "\n\n입고등록 시간: " + stock_at
                + "\n업데이트 시간: " + created_at;

         AlertDialog alertDialog = new AlertDialog.Builder(this).create();
         alertDialog.setTitle(name);
         alertDialog.setMessage(massage);
         alertDialog.show();
        return true;
    }

    @Override
    public void onCameraMoveStarted(int i) {
        // mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }
}
