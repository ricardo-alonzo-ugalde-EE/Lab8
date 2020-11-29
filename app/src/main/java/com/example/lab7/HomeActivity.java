package com.example.lab7;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryDataEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback, ItemClickListener
{
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private static final int REQUEST_FOR_CAMERA = 0011;
    private static final int REQUEST_FOR_LOCATION = 0012;
    private Uri imageUri = null;
    private MyRecyclerAdapter myRecyclerAdapter;
    private LocationCallback locationCallback;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private long UPDATE_INTERVAL = 10 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 2000; /* 2 sec */
    SimpleDateFormat localDateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    private GoogleMap mMap;
    private DatabaseReference ref = FirebaseDatabase.getInstance().getReference("/geofire");
    private GeoFire geoFire = new GeoFire(ref);
    private GeoQuery geoQuery = null;
    private List<String> keyList = null;
    private HashMap<String, PostModel> key_to_Post = null;
    private RecyclerView recyclerView;

    //private Boolean initialLoad =false;
    public void newLocation(Location lastLocation)
    {
        if (geoQuery != null)
            geoQuery.setCenter(new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()));
        else {
            geoQuery = geoFire.queryAtLocation(new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()), 10);
            geoQuery.addGeoQueryDataEventListener(new GeoQueryDataEventListener()
            {
                @Override
                public void onDataEntered(DataSnapshot dataSnapshot, GeoLocation location)
                {
                    final String postKey = dataSnapshot.getKey();
                    if (key_to_Post.containsKey(postKey))
                        return;
                    database.getReference("Posts/" + postKey).addListenerForSingleValueEvent(new ValueEventListener()
                    {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                        {
                            PostModel postModel = new PostModel(dataSnapshot.child("uid").getValue().toString(),
                                    dataSnapshot.child("description").getValue().toString(),
                                    dataSnapshot.child("url").getValue().toString(),
                                    localDateFormat.format(new Date(Long.parseLong(dataSnapshot.child("timestamp").getValue().toString())))
                                    , dataSnapshot.getKey(), mMap.addMarker(new MarkerOptions().position(new LatLng(Double.parseDouble(dataSnapshot.child("lat").getValue().toString()), Double.parseDouble(dataSnapshot.child("lng").getValue().toString())))
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_grey))));

                            key_to_Post.put(postKey, postModel);
                            keyList.add(postKey);
                            myRecyclerAdapter.notifyItemInserted(keyList.size() - 1);
                            recyclerView.scrollToPosition(keyList.size() - 1);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError)
                        {

                        }
                    });


                }

                @Override
                public void onDataExited(DataSnapshot dataSnapshot)
                {

                }

                @Override
                public void onDataMoved(DataSnapshot dataSnapshot, GeoLocation location)
                {

                }

                @Override
                public void onDataChanged(DataSnapshot dataSnapshot, GeoLocation location)
                {

                }

                @Override
                public void onGeoQueryReady()
                {

                }

                @Override
                public void onGeoQueryError(DatabaseError error)
                {

                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        keyList = new ArrayList<>();
        key_to_Post = new HashMap<>();
        recyclerView = findViewById(R.id.recylcer_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        layoutManager.scrollToPosition(0);
        recyclerView.setLayoutManager(layoutManager);
        myRecyclerAdapter = new MyRecyclerAdapter(key_to_Post, keyList, this);
        recyclerView.setAdapter(myRecyclerAdapter);
        //Please take a look at this
        //https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(20);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {

                if (locationResult == null)
                {
                    return;
                }

                Location lastLocation = locationResult.getLastLocation();
                //if(!initialLoad){
                //  initialLoad =true;
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude())).zoom(12).build();
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                // }
                newLocation(lastLocation);
            }
        };
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mapFragment.getMapAsync(this);
        //Source
        //https://developer.android.com/training/permissions/requesting#java
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "We need permission to access your location.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_FOR_LOCATION);
            return;
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper());


    }
    //https://developer.android.com/training/permissions/requesting#java
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        switch (requestCode)
        {
            case REQUEST_FOR_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    //This next check is to eliminate a compilation error. We know that we have the permission here.
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    {

                        return;
                    }
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper());
                }  else {
                    Toast.makeText(this, "The app will not perform correctly without your permission to access the device location.", Toast.LENGTH_SHORT).show();
                }
                return;
        }
        // Other 'case' lines to check for other
        // permissions this app might request.
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.actionbar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.signout:
                mAuth.signOut();
                finish();
                return true;
            case R.id.newUser:
                createTestEntry();
                return true;
            case R.id.edit_profile:
                startActivity(new Intent(this, EditProfile.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void createTestEntry()
    {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("Users");
        String pushKey = usersRef.push().getKey();
        usersRef.child(pushKey).setValue(new User("Test Display Name",
                "Test Email", "Test Phone"));
    }

    public void uploadNewPhoto(View view)
    {
        checkPermissions();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        myRecyclerAdapter.removeListener();
    }

    private void takePhoto() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From your Camera");
        imageUri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        Intent chooser = Intent.createChooser(intent, "Select a Camera App.");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(chooser, REQUEST_FOR_CAMERA);
        }
    }

    private void checkPermissions() {

        if (ContextCompat.checkSelfPermission(getBaseContext(),
                android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getBaseContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "We need permission to access your camera and photo.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_FOR_CAMERA);
        } else {
            takePhoto();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOR_CAMERA && resultCode == RESULT_OK) {
            if (imageUri == null) {
                Toast.makeText(this, "Error taking photo.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, PhotoPreview.class);
            intent.putExtra("uri", imageUri.toString());
            startActivity(intent);

            return;
        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.silvermapstyle));

            if (!success) {
                Log.e("UTARZ", "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e("UTARZ", "Can't find style. Error: ", e);
        }
        mMap = googleMap;

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                //show the image
                Toast.makeText(HomeActivity.this, "Marker clicked", Toast.LENGTH_SHORT).show();
                return false;
            }
        });
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mMap.setMyLocationEnabled(true);

    }

    @Override
    public void onItmeClick(LatLng latLng) {
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(latLng).zoom(12).build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    public void openNewActivity(View view)
    {
        Intent intent = new Intent(this, ChatWindow.class);
        startActivity(intent);
    }
}
