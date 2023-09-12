package com.spark.mapapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import spark.mapapp.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


class MainActivity : AppCompatActivity(), PlaceSelectionListener, OnMapReadyCallback {

    var dataPoints = ArrayList<LatLng>()
    private var sourceLatLng: LatLng? = null
    private var destinationLatLng: LatLng? = null

    private lateinit var cardView: CardView
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionCode = 123
    private lateinit var autocompleteFragment: AutocompleteSupportFragment


    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerList: ListView
    private var polylineString = ""
    private var saved = false;

    // Declare new views
    private lateinit var sourceTextView: TextView
    private var setUpFrom: String = "source"
    private val selectedDestinations: MutableList<Place> = mutableListOf()
    private lateinit var destinationRecyclerView: RecyclerView
    private lateinit var destinationAdapter: DestinationAdapter

    private lateinit var addDestinationTextView: TextView
    private lateinit var drawPathBtn: MaterialButton
    lateinit var export: TextView
    lateinit var import: TextView

    private var added: Boolean = false
//    val sharedPref: SharedPreferences = this.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    private lateinit var sharedPref: SharedPreferences

    private var mBottomSheetBehavior: BottomSheetBehavior<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        sharedPref = this.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        //getProductData();
        val saveFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    saveToFile(uri)
                    savePoints(uri)
                }
            }
        }
         val openFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    openAndReadFile(uri)
                }
            }
        }
        val bottomSheet = findViewById<View>(R.id.bottom_sheet)
        drawPathBtn = findViewById(R.id.drawPathBtn)

        mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)


        (mBottomSheetBehavior as BottomSheetBehavior<*>).isHideable = false
        (mBottomSheetBehavior as BottomSheetBehavior<*>).addBottomSheetCallback(object :
            BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED ->                         // mTextViewState.setText("Collapsed");
                        findViewById<TextView>(R.id.slide_tv).text = "Go To"

                    BottomSheetBehavior.STATE_DRAGGING -> {}
                    BottomSheetBehavior.STATE_EXPANDED ->                         //  mTextViewState.setText("Expanded");
                        findViewById<TextView>(R.id.slide_tv).text = "Hide Details"

                    BottomSheetBehavior.STATE_HIDDEN -> {}
                    BottomSheetBehavior.STATE_SETTLING -> {}
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        TODO()
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                //mTextViewState.setText("Sliding...");
            }
        })


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Request location permission if not granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE
                ),
                locationPermissionCode
            )
        }




        Places.initialize(applicationContext, getString(R.string.api))
        cardView = findViewById(R.id.idCardView)

        findViewById<ImageView>(R.id.search).setOnClickListener {
            cardView.visibility = View.VISIBLE

            // Initialize AutocompleteSupportFragment
            autocompleteFragment =
                supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                        as AutocompleteSupportFragment
            autocompleteFragment.setPlaceFields(
                listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG
                )
            )
            autocompleteFragment.setOnPlaceSelectedListener(this)

        }
        sourceTextView = findViewById(R.id.sourceTextView)
        export = findViewById(R.id.export)
        import = findViewById(R.id.importe)
        sourceTextView.setOnClickListener {
            cardView.visibility = View.VISIBLE
            setUpFrom = "source"
            // Initialize AutocompleteSupportFragment
            autocompleteFragment =
                supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                        as AutocompleteSupportFragment
            autocompleteFragment.setPlaceFields(
                listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG
                )
            )
            autocompleteFragment.setOnPlaceSelectedListener(this)
        }
        // Inside onCreate or onCreateView method
        destinationRecyclerView = findViewById(R.id.destinationRecyclerView)
        addDestinationTextView = findViewById(R.id.addDestinationTextView)
        // Initialize the RecyclerView adapter with the selectedDestinations list and the delete button click listener
        destinationAdapter = DestinationAdapter(selectedDestinations) { position ->
            // Remove the destination from the list at the clicked position
            selectedDestinations.removeAt(position)
            // Notify the adapter about the data change
            destinationAdapter.notifyDataSetChanged()
        }
        destinationRecyclerView.adapter = destinationAdapter
        destinationRecyclerView.layoutManager = LinearLayoutManager(this)
        addDestinationTextView.setOnClickListener {
            cardView.visibility = View.VISIBLE
            setUpFrom = "destination"

            added = false
            // Initialize AutocompleteSupportFragment
            autocompleteFragment =
                supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                        as AutocompleteSupportFragment
            autocompleteFragment.setPlaceFields(
                listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG
                )
            )
            autocompleteFragment.setOnPlaceSelectedListener(this)
        }

        // Fetching API_KEY which we wrapped
        val ai: ApplicationInfo = applicationContext.packageManager
            .getApplicationInfo(applicationContext.packageName, PackageManager.GET_META_DATA)
        val value = ai.metaData["com.google.android.geo.API_KEY"]
        val apiKey = value.toString()

        // Initializing the Places API with the help of our API_KEY
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }



        drawPathBtn.setOnClickListener {
            if (sourceLatLng != null && selectedDestinations.isNotEmpty()) {
                val destinationsLatLng = selectedDestinations.map { it.latLng }
                drawPathsFromSourceToDestinations(
                    map,
                    sourceLatLng!!,
                    destinationsLatLng,
                    getString(R.string.api)
                )
            } else {
                Toast.makeText(this, "Set source and destinations first", Toast.LENGTH_SHORT).show()
            }
        }




        drawerLayout = findViewById(R.id.drawer_layout)
        drawerList = findViewById(R.id.drawer_list)

        findViewById<ImageView>(R.id.leftAction).setOnClickListener {

            // Open the navigation drawer
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Set up the adapter for the navigation drawer menu items
        val drawerItems = arrayOf("Item 1", "Item 2", "Item 3", "Item 4", "Item 5")
        val drawerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, drawerItems)
        drawerList.adapter = drawerAdapter

        Log.d("items", drawerAdapter.count.toString())
        // Handle item clicks in the navigation drawer
        drawerList.setOnItemClickListener { _, _, position, _ ->
            // Close the navigation drawer
            drawerLayout.closeDrawer(GravityCompat.START)

            // Handle the selected item
            when (position) {
                0 -> {
                    // Handle item 1 click

                }

                1 -> {
                    // Handle item 2 click
                }

                2 -> {
                    // Handle item 3 click
                }
            }
        }

        findViewById<ImageView>(R.id.close).setOnClickListener {

            // Open the navigation drawer
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        import.setOnClickListener {
            showFilePickerForOpen(openFileLauncher)
            //Import
//            val contentResolver: ContentResolver = applicationContext.contentResolver
//
//            val projection = arrayOf(
//                MediaStore.MediaColumns._ID,
//                MediaStore.MediaColumns.DISPLAY_NAME
//            )
//
//            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
//            val selectionArgs = arrayOf("route_data.txt")
//
//            val queryUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
//
//            contentResolver.query(queryUri, projection, selection, selectionArgs, null)
//                ?.use { cursor ->
//                    if (cursor.moveToFirst()) {
//                        val idColumn: Int =
//                            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
//                        val id = cursor.getLong(idColumn)
//                        val uri = Uri.withAppendedPath(queryUri, id.toString())
//
//                        contentResolver.openInputStream(uri)?.use { inputStream ->
//                            val reader = BufferedReader(InputStreamReader(inputStream))
//                            val stringBuilder = StringBuilder()
//                            var line: String? = reader.readLine()
//                            while (line != null) {
//                                stringBuilder.append(line)
//                                line = reader.readLine()
//                            }
//                            val jsonString: String = stringBuilder.toString()
//                            val jsonObject = JSONObject(jsonString)
//                            val polylineString = jsonObject.getString("polylineString")
//                            val getDestinationsLatLng = jsonObject.getString("getDestinationsLatLng")
//                            val getSourceLatLngString = jsonObject.getString("getSourceLatLngString")
//
////                            val polylineString: String = stringBuilder.toString()
//                            showitOnMap(polylineString,getSourceLatLngString,getDestinationsLatLng)
//                        }
//                    } else {
//                        Toast.makeText(
//                            this@MainActivity,
//                            "No rout available to export",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//                }
        }





        export.setOnClickListener {
            if (saved) {
                showFilePicker(saveFileLauncher)
//                //Import
//                val contentResolver: ContentResolver = applicationContext.contentResolver
//
//                val projection = arrayOf(
//                    MediaStore.MediaColumns._ID,
//                    MediaStore.MediaColumns.DISPLAY_NAME
//                )
//
//                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
//                val selectionArgs = arrayOf("route_data.txt")
//
//                val queryUri: Uri =
//                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
//
//                contentResolver.query(queryUri, projection, selection, selectionArgs, null)
//                    ?.use { cursor ->
//                        val idColumnIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
//
//                        if (idColumnIndex == -1) {
//                            // Handle case where _ID column is not found
//                            Toast.makeText(
//                                this@MainActivity,
//                                "Column _ID not found in the cursor",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                            return@use
//                        }
//
//                        if (cursor.moveToFirst()) {
//                            val fileId = cursor.getLong(idColumnIndex)
//                            if (fileId >= 0) {
//                                val fileUri = ContentUris.withAppendedId(queryUri, fileId)
//
//                                // Delete the file using the content resolver
//                                contentResolver.delete(fileUri, null, null)
//
//                                createRoute()
//                            } else {
//                                Toast.makeText(
//                                    this@MainActivity,
//                                    "Invalid fileId",
//                                    Toast.LENGTH_SHORT
//                                ).show()
//                            }
//                        } else {
//                            createRoute()
//                        }
//                    }
//
//
            } else {
                Toast.makeText(this@MainActivity, "Please create a route first", Toast.LENGTH_SHORT)
                    .show()

            }
        }



    }
    private fun showFilePickerForOpen(openFileLauncher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain" // Set the MIME type for JSON files
        }
        openFileLauncher.launch(intent)
    }
    private fun openAndReadFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                val stringBuilder = StringBuilder()
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line)
                    line = reader.readLine()
                }
                val jsonString: String = stringBuilder.toString()
                val jsonObject = JSONObject(jsonString)
                val polylineString = jsonObject.getString("polylineString")
                val getDestinationsLatLng = jsonObject.getString("getDestinationsLatLng")
                val getSourceLatLngString = jsonObject.getString("getSourceLatLngString")

                // Now you have the data from the JSON file, perform further processing
                showitOnMap(polylineString, getSourceLatLngString, getDestinationsLatLng)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening and reading file", e)
            Toast.makeText(this@MainActivity, "Error opening file", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showFilePicker(saveFileLauncher: ActivityResultLauncher<Intent>) {
        val time = System.currentTimeMillis()

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain" // Set the MIME type for JSON files
            putExtra(Intent.EXTRA_TITLE, "$time.txt") // Default file name
        }
        saveFileLauncher.launch(intent)
    }

    private fun saveToFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val json = sharedPref.getString("destinationsLatLngStrings", null)
                val gSourceLatLngString = sharedPref.getString("sourceLatLngString", "")

                val jsonObject = JSONObject()
                jsonObject.put("polylineString", polylineString)
                jsonObject.put("getDestinationsLatLng", json)
                jsonObject.put("getSourceLatLngString", gSourceLatLngString)
                val jsonString = jsonObject.toString()

                outputStream.write(jsonString.toByteArray())
            }
            Toast.makeText(
                this@MainActivity,
                "Route exported successfully",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error exporting route", e)
            Toast.makeText(this@MainActivity, "Error exporting route", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun createRoute() {
        saved = false
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "route_data.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        val resolver = contentResolver
        val uri = resolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        )
        val json = sharedPref.getString("destinationsLatLngStrings", null)
        val gSourceLatLngString = sharedPref.getString("sourceLatLngString", "");


        val jsonObject = JSONObject()
        jsonObject.put("polylineString", polylineString)
        jsonObject.put("getDestinationsLatLng", json)
        jsonObject.put("getSourceLatLngString", gSourceLatLngString)
        val jsonString = jsonObject.toString()

        uri?.let {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }
            Toast.makeText(
                this@MainActivity,
                "Route exported successfully",
                Toast.LENGTH_SHORT
            ).show()
        } ?: run {
            Toast.makeText(this@MainActivity, "Error exporting route", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun showitOnMap(
        polylineString: String,
        sourceLatLngString: String,
        getDestinationsLatLng: String
    ) {
        val json = getDestinationsLatLng
        val type = object : TypeToken<List<String>>() {}.type
        val gDestinationsLatLngStrings: List<String> = Gson().fromJson(json, type) ?: emptyList()
        val getDestinationsLatLng: List<LatLng> = gDestinationsLatLngStrings.map { latLngString ->
            val components = latLngString.split(",")

            if (components.size == 2) {
                val latitude = components[0].toDouble()
                val longitude = components[1].toDouble()
                LatLng(latitude, longitude)
            } else {
                // Handle invalid input
                LatLng(0.0, 0.0) // Default value or handle error
            }
        }
        var getSourceLatLngString: LatLng? = null
        val gSourceLatLngString = sourceLatLngString;
        val components = gSourceLatLngString!!.split(",")
        if (components.size == 2) {
            val latitude = components[0].toDouble()
            val longitude = components[1].toDouble()
            getSourceLatLngString = LatLng(latitude, longitude)
            // Now latLng contains the LatLng object
        } else {
            // Handle invalid input
        }
        var body = sharedPref.getString("body", "");


        showOnTheMap(getSourceLatLngString, getDestinationsLatLng, polylineString)

    }


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = false

        // Move the camera to the current user location if location permission is granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))


                        setUpDrag(currentLatLng)
                        animateAndMark(currentLatLng)

                    }
                }
        }

        map.setOnMapClickListener {

            animateAndMark(it)
            setUpDrag(it)

        }


    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun animateAndMark(it: LatLng) {
        val markerOptions = MarkerOptions()
        markerOptions.position(it)
        markerOptions.title(it.latitude.toString() + " : " + it.longitude)
        map.clear()
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 18f))
        map.addMarker(markerOptions)

        GlobalScope.launch {
            val address = getAddressFromLatLng(it.latitude, it.longitude)

            withContext(Dispatchers.Main) {
                if ("destination" == setUpFrom) {
                    // destinationRecyclerView = address
                    destinationLatLng = it

                    if (!added) {

                        val place = Place.builder()
                            .setName(getAddressFromLatLng(it.latitude, it.longitude))
                            .setAddress(address)
                            .setLatLng(it)
                            .build()

                        selectedDestinations.add(place)

                        // Notify the RecyclerView adapter about the changes
                        destinationAdapter.notifyDataSetChanged()
                    }
                } else {
                    sourceTextView.text = address
                    sourceLatLng = it
                }
            }
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("PotentialBehaviorOverride")
    private fun setUpDrag(it: LatLng) {
        val markerOptions = MarkerOptions()
        markerOptions.position(it)
        markerOptions.title(it.latitude.toString() + " : " + it.longitude)

        map.clear()
        val marker = map.addMarker(markerOptions)

        // Enable dragging for the marker
        marker?.isDraggable = true

        // Set the marker drag listener
        map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {
                // Optional: You can perform any action when dragging starts
            }

            override fun onMarkerDrag(marker: Marker) {
                // Optional: You can perform any action while dragging
            }

            override fun onMarkerDragEnd(marker: Marker) {
                // This function is called when the user stops dragging the marker
                // Retrieve the final position of the marker
                val position = marker.position

                // Update the camera position to the dragged marker's position
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 18f))

                // Use a coroutine to get the address in the background
                GlobalScope.launch {
                    val address = getAddressFromLatLng(position.latitude, position.longitude)

                    // Update the UI with the address in the main thread
                    withContext(Dispatchers.Main) {
                        if ("destination" == setUpFrom) {
                            destinationLatLng = position

                            if (!added) {

                                val place = Place.builder()
                                    .setName(getAddressFromLatLng(it.latitude, it.longitude))
                                    .setAddress(address)
                                    .setLatLng(position)
                                    .build()

                                selectedDestinations.add(place)

                                // Notify the RecyclerView adapter about the changes
                                destinationAdapter.notifyDataSetChanged()
                            }
                        } else {
                            sourceTextView.text = address
                            sourceLatLng = position
                        }
                    }
                }
            }
        })
    }

    private fun drawPathsFromSourceToDestinations(
        map: GoogleMap,
        sourceLatLng: LatLng,
        destinationsLatLng: List<LatLng>,
        apiKey: String
    ) {
        // Create a Retrofit instance
        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/maps/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // Create the service instance
        val directionsApi = retrofit.create(DirectionsApiService::class.java)

        // Create a list to hold the waypoints for the route
        val waypoints = mutableListOf<String>()

        // Add all destinations as waypoints in the desired sequence
        for (destinationLatLng in destinationsLatLng) {
            waypoints.add("${destinationLatLng.latitude},${destinationLatLng.longitude}")
        }

        // Join the waypoints into a single string separated by pipe (|) character
        val waypointsString = waypoints.joinToString("|")

        // Request directions from the Directions API for the route including all waypoints
        directionsApi.getDirections(
            "${sourceLatLng.latitude},${sourceLatLng.longitude}",
            "${destinationsLatLng.last().latitude},${destinationsLatLng.last().longitude}",
            apiKey,
            waypointsString // Pass the waypoints string to the API request
        ).enqueue(object : Callback<DirectionsResponse> {
            override fun onResponse(
                call: Call<DirectionsResponse>,
                response: Response<DirectionsResponse>
            ) {
                if (response.isSuccessful) {
                    val body = response.body()




                    Log.d("bodyAndResponse", body.toString() + "\n" + response)
                    if (body != null) {
                        val route = body.routes.firstOrNull()
                        if (route?.overviewPolyline != null) {
                            val points = PolyUtil.decode(route.overviewPolyline.points)
                            for (point in points) {
                                dataPoints.add(point)
                            }
                            // Encode the points into a polyline string
                            polylineString = PolyUtil.encode(points)
                            val sourceLatLngString =
                                "${sourceLatLng.latitude},${sourceLatLng.longitude}"
                            val destinationsLatLngStrings: List<String> =
                                destinationsLatLng.map { latLng ->
                                    "${latLng.latitude},${latLng.longitude}"
                                }
                            val gson = Gson()
                            val json = gson.toJson(destinationsLatLngStrings)

                            val editor = sharedPref.edit()
                            editor.putString("destinationsLatLngStrings", json)
                            editor.putString("sourceLatLngString", sourceLatLngString)
                            editor.putString("body", body.toString())
                            editor.apply()

                            saved = true
                            // Draw the path on the map
                            val polylineOptions = PolylineOptions()
                                .addAll(points)
                                .width(10f)
                                .color(Color.parseColor("#4a89f3"))
                            map.addPolyline(polylineOptions)

                            // Move the camera to the bounds of the route
                            val boundsBuilder = LatLngBounds.Builder()
                                .include(sourceLatLng)
                                .include(destinationsLatLng.last())
                            for (destinationLatLng in destinationsLatLng) {
                                boundsBuilder.include(destinationLatLng)
                            }
                            val bounds = boundsBuilder.build()
                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                        } else {
                            // Handle the case when no route is found
                            Toast.makeText(
                                this@MainActivity,
                                "No route found.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // Handle error in response
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${response.message()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                // Handle network failure or other errors
                Toast.makeText(
                    this@MainActivity,
                    "Network Error: ${t.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun savePoints(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val gson = Gson()
                val formatedList = mutableListOf<HashMap<String, Double>>()
                for (latLng in dataPoints) {
                    val latlngMap = hashMapOf(
                        "latitude" to latLng.latitude,
                        "longitude" to latLng.longitude
                    )
                    formatedList.add(latlngMap)
                    Log.d("Saved LatLng", latlngMap.toString())
                }
                val json = gson.toJson(formatedList)
                outputStream.write(json.toByteArray())
            }
            Toast.makeText(
                this@MainActivity,
                "Route exported successfully",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error exporting route", e)
            Toast.makeText(this@MainActivity, "Error exporting route", Toast.LENGTH_SHORT)
                .show()
        }
    }


    private fun showOnTheMap(
        sourceLatLngString: LatLng?,
        destinationsLatLng: List<LatLng>,
        polylineString: String
    ) {
        val points = PolyUtil.decode(polylineString)
        saved = true
        // Draw the path on the map
        val polylineOptions = PolylineOptions()
            .addAll(points)
            .width(10f)
            .color(Color.parseColor("#4a89f3"))
        map.addPolyline(polylineOptions)

        // Move the camera to the bounds of the route
        val boundsBuilder = LatLngBounds.Builder()
            .include(sourceLatLngString!!)
            .include(destinationsLatLng.last())
        for (destinationLatLng in destinationsLatLng) {
            boundsBuilder.include(destinationLatLng)
        }
        val bounds = boundsBuilder.build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))


    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, move the camera to current location
                if (::map.isInitialized) {
                    onMapReady(map)
                }
            }
        }
    }

    override fun onPlaceSelected(place: Place) {
        // Handle the selected place
        cardView.visibility = View.GONE

        val latLng = place.latLng
        if (latLng != null) {

            added = true
            selectedDestinations.add(place)
            // Notify the RecyclerView adapter about the changes
            destinationAdapter.notifyDataSetChanged()
            animateAndMark(latLng)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }


    }

    override fun onError(status: Status) {
        // Handle the error

        cardView.visibility = View.GONE

    }


    private suspend fun getAddressFromLatLng(latitude: Double, longitude: Double): String {
        return withContext(Dispatchers.IO) {
            val geocoder = Geocoder(applicationContext)
            var addressText = ""

            try {
                val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1)

                if (addresses.isNotEmpty()) {
                    val address: Address = addresses[0]
                    addressText = address.getAddressLine(0)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            addressText
        }
    }


}

