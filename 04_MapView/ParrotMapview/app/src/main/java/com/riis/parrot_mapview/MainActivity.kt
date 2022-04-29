package com.riis.parrot_mapview
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.parrot.drone.groundsdk.GroundSdk
import com.parrot.drone.groundsdk.ManagedGroundSdk
import com.parrot.drone.groundsdk.Ref
import com.parrot.drone.groundsdk.device.DeviceState
import com.parrot.drone.groundsdk.device.Drone
import com.parrot.drone.groundsdk.device.instrument.Altimeter
import com.parrot.drone.groundsdk.device.instrument.BatteryInfo
import com.parrot.drone.groundsdk.device.instrument.Gps
import com.parrot.drone.groundsdk.device.pilotingitf.Activable
import com.parrot.drone.groundsdk.device.pilotingitf.FlightPlanPilotingItf
import com.parrot.drone.groundsdk.device.pilotingitf.ManualCopterPilotingItf
import com.parrot.drone.groundsdk.facility.AutoConnection
import com.parrot.drone.groundsdk.mavlink.standard.*
import com.parrot.drone.groundsdk.mavlink.standard.NavigateToWaypointCommand.Companion
import java.io.File


val TAG = "Sussy"

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener, View.OnClickListener{

    private lateinit var groundSdk:GroundSdk
    private var drone: Drone? = null
    private var droneStateRef: Ref<DeviceState>? = null
    private var droneBatteryInfoRef: Ref<BatteryInfo>? = null
    private var droneGPSInfoRef : Ref<Gps>? = null
    private var droneAltitudeInfoRef : Ref<Altimeter>? = null
    private var pilotingItfRef: Ref<ManualCopterPilotingItf>? = null
    private var flightPlanPilotingItfRef: Ref<FlightPlanPilotingItf>? = null


    private lateinit var droneStateTxt: TextView
    private lateinit var droneBatteryTxt: TextView
    private lateinit var latitudeTxt: TextView
    private lateinit var longitudeTxt: TextView
    private lateinit var altitudeTxt: TextView

    // configuration buttons
    private lateinit var locate: Button
    private lateinit var add: Button
    private lateinit var clear: Button
    private lateinit var config: Button
    private lateinit var generate: Button
    private lateinit var start: Button
    private lateinit var stop: Button

    private var droneMarker: Marker? = null
    private var isAdd = false

    private lateinit var mMap: GoogleMap
    private val mMarkers: HashMap<Int, Marker?> = HashMap()
    private lateinit var mavlinkFile : File

    //mission settings
    private val missionList = mutableListOf<MavlinkCommand>()
    private val waypointList = mutableListOf<LatLng>()
    private var mAltitude : Double = 100.0
    private var mSpeed : Double = 10.0
    private var mFinishedAction :String = "gohome"

    private fun initUi() {
        latitudeTxt = findViewById(R.id.labelDroneLat)
        longitudeTxt = findViewById(R.id.labelDroneLng)
        altitudeTxt = findViewById(R.id.altitudeTxt)
        droneStateTxt = findViewById(R.id.droneStateTxt)
        droneBatteryTxt = findViewById(R.id.droneBatteryTxt)

        locate = findViewById(R.id.locate)
        add = findViewById(R.id.add)
        clear = findViewById(R.id.clear)
        config = findViewById(R.id.config)
        generate = findViewById(R.id.generate)
        start = findViewById(R.id.start)
        stop = findViewById(R.id.stop)

        locate.setOnClickListener(this)
        add.setOnClickListener(this)
        clear.setOnClickListener(this)
        config.setOnClickListener(this)
        generate.setOnClickListener(this)
        start.setOnClickListener(this)
        stop.setOnClickListener(this)

        start.isEnabled = false
        stop.isEnabled = false
        config.isEnabled = false
        generate.isEnabled = false
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initUi()
        droneStateTxt.text = DeviceState.ConnectionState.DISCONNECTED.toString()
        groundSdk = ManagedGroundSdk.obtainSession(this)
    }

    override fun onStart() {
        super.onStart()

        groundSdk.getFacility(AutoConnection::class.java) {
            it?.let{
                if (it.status != AutoConnection.Status.STARTED) {
                    it.start()
                }
                if (drone?.uid != it.drone?.uid) {
                    stopDroneMonitors()
                    resetDroneUi()

                    drone = it.drone
                    startDroneMonitors()
                }
            }
        }
    }

    private fun startDroneMonitors() {
        monitorDroneStates()

        monitorDroneBatteryLevel()
        monitorDroneGPS()
        monitorDroneAltitude()
        monitorPilotingInterface()
    }

    private fun stopDroneMonitors() {
        droneStateRef?.close()
        droneStateRef = null

        droneBatteryInfoRef?.close()
        droneBatteryInfoRef = null

        pilotingItfRef?.close()
        pilotingItfRef = null

        // autonomous pilot
        flightPlanPilotingItfRef?.close()
        flightPlanPilotingItfRef = null

        droneGPSInfoRef?.close()
        droneGPSInfoRef = null

        droneAltitudeInfoRef?.close()
        droneAltitudeInfoRef = null
    }

    private fun monitorDroneStates() {
        droneStateRef = drone?.getState {
            it?.let {
                droneStateTxt.text = it.connectionState.toString()
                if (it.connectionState == DeviceState.ConnectionState.DISCONNECTED){
                    Toast.makeText(this, "The drone will now return home if in a mission", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun monitorDroneBatteryLevel() {
        droneBatteryInfoRef = drone?.getInstrument(BatteryInfo::class.java) {
            it?.let {
                droneBatteryTxt.text = "${it.batteryLevel} %"
            }
        }
    }

    private fun monitorDroneGPS(){
        droneGPSInfoRef =  drone?.getInstrument(Gps::class.java) { gps ->
            gps?.lastKnownLocation().let { location ->
                ("lng: " + location?.latitude.toString()).also { latitudeTxt.text = it }
                ("lat: " + location?.longitude.toString()).also { longitudeTxt.text = it }
//                Log.d(TAG, "Updated Location: ${it?.latitude}, ${it?.longitude}")
                if (location != null) {
                    updateDroneLocation(location.latitude, location.longitude)
                    cameraUpdate(location.latitude, location.longitude)
                }
            }
        }
    }

    private fun monitorDroneAltitude(){
        droneAltitudeInfoRef =  drone?.getInstrument(Altimeter::class.java) { altimeter ->
            altimeter?.groundRelativeAltitude.let { altitude ->
                ("alt: " + altitude?.value.toString()).also { altitudeTxt.text = it }
            }
        }
    }

    private fun monitorPilotingInterface() {
        // autonomous piloting
        flightPlanPilotingItfRef = drone?.getPilotingItf(FlightPlanPilotingItf::class.java){
            if (it != null){
                manageAutoPilotingItfState(it)
            }
        }
    }

    private fun manageAutoPilotingItfState(itf: FlightPlanPilotingItf) {
        when (itf.state) {
            Activable.State.UNAVAILABLE -> {
                Log.d(TAG, "the state is unavailable")
            }
            Activable.State.IDLE -> {
//                val status = itf.activate(true)
//                Log.d(TAG, "activation status: $status - state is idle")
            }

            Activable.State.ACTIVE -> {
                Log.d(TAG, "state is active")
            }

        }
    }

    private fun resetDroneUi() {
        droneStateTxt.text = DeviceState.ConnectionState.DISCONNECTED.toString()
        droneBatteryTxt.text = ""
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.locate -> { // will draw the drone and move camera to the position of the drone on the map
                val location =  drone?.getInstrument(Gps::class.java)?.lastKnownLocation()
                latitudeTxt.text = location?.latitude.toString()
                longitudeTxt.text = location?.longitude.toString()
                Log.d(TAG, "Location on Btn Click: ${location?.latitude}, ${location?.longitude}")
                if (location != null) {
                    updateDroneLocation(location.latitude, location.longitude)
                    cameraUpdate(location.latitude, location.longitude)
                }
            }
            R.id.add -> { // this will toggle the adding of the waypoints
                enableDisableAdd()
            }
            R.id.clear -> { // clear the waypoints on the map
                runOnUiThread {
                    mMap.clear()
                    clearMission()
                }
            }
            R.id.config -> { // this will show the settings
                showSettingsDialog()
            }
            R.id.generate -> { // this will upload the mission to the drone so that it can execute it
                generateMission()
            }
            R.id.start -> { // this will let the drone start navigating to the waypoints
                startMission()
            }
            R.id.stop -> { // this will immediately stop the waypoint mission
                stopMission()
            } else -> {}
        }
    }

    private fun startMission() {
        val missionControl = drone?.getPilotingItf(FlightPlanPilotingItf::class.java)
        if (missionControl != null){
            missionControl.clearRecoveryInfo()
            Log.d(TAG, mavlinkFile.absolutePath)
            missionControl.uploadFlightPlan(mavlinkFile)
            missionControl.returnHomeOnDisconnect.isEnabled = true
            Log.d(TAG,  "latest mission: " + missionControl.latestMissionItemExecuted.toString())
            Log.d(TAG,  "it.returnHomeOnDisconnect: " + missionControl.returnHomeOnDisconnect.isEnabled.toString())
            if(missionControl.state == Activable.State.IDLE){
                val missionStarted = missionControl.activate(true)
                if (missionStarted){
                    Toast.makeText(this, "mission started", Toast.LENGTH_SHORT).show()
                }else{
                    Toast.makeText(this, "mission couldn't be started", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopMission(){
        val missionControl = drone?.getPilotingItf(FlightPlanPilotingItf::class.java)
        val isStopped = missionControl?.stop()
        if (isStopped == true){
            Toast.makeText(this, "mission has been stopped", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this, "mission couldn't be stopped", Toast.LENGTH_SHORT).show()
        }
        Log.d(TAG, "mission is stopped = $isStopped")
    }

    private fun generateMission(){
        Toast.makeText(this, "Generating mavlink mission file...", Toast.LENGTH_SHORT).show()

        val location =  drone?.getInstrument(Gps::class.java)?.lastKnownLocation()

        missionList.add(0, ChangeSpeedCommand(ChangeSpeedCommand.SpeedType.GROUND_SPEED, mSpeed, false))

        if (location != null){
            when (mFinishedAction) {
                "gohome" -> {
                    missionList.add(1, TakeOffCommand(location.latitude, location.longitude, 0.0,0.0))
                    waypointList.forEach{ point ->
                        missionList.add(
                            NavigateToWaypointCommand(
                                point.latitude, point.longitude, mAltitude,0.0, Companion.DEFAULT_HOLD_TIME, Companion.DEFAULT_ACCEPTANCE_RADIUS
                            )
                        )
                    }
                    missionList.add(ReturnToLaunchCommand())
                    missionList.add(LandCommand(location.latitude, location.longitude, 0.0,0.0))
                }
                "autoland" -> {
                    missionList.add(1, TakeOffCommand(location.latitude, location.longitude, 0.0,0.0))
                    waypointList.forEach{ point ->
                        missionList.add(
                            NavigateToWaypointCommand(
                                point.latitude, point.longitude, mAltitude,0.0, Companion.DEFAULT_HOLD_TIME, Companion.DEFAULT_ACCEPTANCE_RADIUS
                            )
                        )
                    }
                    missionList.add(LandCommand(location.latitude, location.longitude, 0.0,0.0))
                }
                "none" -> {
                    missionList.add(1, TakeOffCommand(location.latitude, location.longitude, 0.0,0.0))
                    waypointList.forEach{ point ->
                        missionList.add(
                            NavigateToWaypointCommand(
                                point.latitude, point.longitude, mAltitude,0.0, Companion.DEFAULT_HOLD_TIME, Companion.DEFAULT_ACCEPTANCE_RADIUS
                            )
                        )
                    }
                }
                "firstwaypoint" -> {
                    missionList.add(1, TakeOffCommand(location.latitude, location.longitude, 0.0,0.0))
                    waypointList.forEach{ point ->
                        missionList.add(
                            NavigateToWaypointCommand(
                                point.latitude, point.longitude, mAltitude,0.0, Companion.DEFAULT_HOLD_TIME, Companion.DEFAULT_ACCEPTANCE_RADIUS
                            )
                        )
                    }
                    missionList.add(missionList[2])
                    missionList.add(LandCommand(location.latitude, location.longitude, 0.0,0.0))
                }
            }
        }



        val folder = getExternalFilesDir("flight_plan")
        mavlinkFile = File(folder, "flight_plan.txt")

        MavlinkFiles.generate(
            mavlinkFile,
            missionList,
            "QGC WPL 120"
        )
        start.isEnabled = true
        stop.isEnabled = true
    }

    private fun clearMission(){
        Toast.makeText(this, "Cleared the current mission. Please recreate it again...", Toast.LENGTH_SHORT).show()
        missionList.clear()
        waypointList.clear()
        config.isEnabled = false
        start.isEnabled = false
        generate.isEnabled = false
        stop.isEnabled = false
    }



    private fun updateDroneLocation(latitude: Double, longitude: Double) { // this will draw the aircraft as it moves
        if (latitude.isNaN() || longitude.isNaN())  { return }

        val pos = LatLng(latitude, longitude)
        // the following will draw the aircraft on the screen
        val markerOptions = MarkerOptions()
            .position(pos)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft))
        runOnUiThread {
            droneMarker?.remove()
            if (checkGpsCoordination(latitude, longitude)) {
                droneMarker = mMap.addMarker(markerOptions)
            }
        }
    }

    private fun enableDisableAdd() { // toggle for adding or not
        if (!isAdd) {
            isAdd = true
            add.text = "Exit"
            config.isEnabled = false
        } else {
            isAdd = false
            add.text = "Add"
            config.isEnabled = true
        }
    }

    private fun checkGpsCoordination(latitude: Double, longitude: Double): Boolean { // this will check if your gps coordinates are valid
        return latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180 && latitude != 0.0 && longitude != 0.0
    }

    private fun setUpMap() {
        mMap.setOnMapClickListener(this) // add the listener for click for amap object
    }


    private fun cameraUpdate(latitude: Double, longitude: Double) {
        val pos = LatLng(latitude, longitude)
        val zoomLevel = 18.0.toFloat()
        val cu = CameraUpdateFactory.newLatLngZoom(pos, zoomLevel)
        mMap.moveCamera(cu)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setUpMap()
    }



    override fun onMapClick(point: LatLng) {
        if (isAdd) {
            markWaypoint(point)
            waypointList.add(point)
//            val mWaypoint = NavigateToWaypointCommand(point.latitude, point.longitude, mAltitude,0.0, Companion.DEFAULT_HOLD_TIME, Companion.DEFAULT_ACCEPTANCE_RADIUS)
//            missionList.add(mWaypoint)
        } else {
            Toast.makeText(this, "Cannot Add Waypoint", Toast.LENGTH_SHORT).show()
        }
    }

    private fun markWaypoint(point: LatLng) {
        //Create MarkerOptions object
        val markerOptions = MarkerOptions()
        markerOptions.position(point)
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        val marker: Marker? = mMap.addMarker(markerOptions)
        mMarkers[mMarkers.size] = marker
    }

    private fun showSettingsDialog() {
        val wayPointSettings = layoutInflater.inflate(R.layout.dialog_waypointsetting, null) as LinearLayout

        val altitudeEditText = wayPointSettings.findViewById<View>(R.id.altitude) as EditText

        val speedEditText = wayPointSettings.findViewById<View>(R.id.speed) as EditText

        val actionAfterFinishedRG = wayPointSettings.findViewById<View>(R.id.actionAfterFinished) as RadioGroup
        actionAfterFinishedRG.setOnCheckedChangeListener { _, checkedId -> // set the action after finishing the mission
            Log.d(TAG, "Select finish action")

            when (checkedId) {
                R.id.finishNone -> {
                    mFinishedAction = "none"
                }
                R.id.finishGoHome -> {
                    mFinishedAction = "gohome"
                }
                R.id.finishAutoLanding -> {
                    mFinishedAction = "autoland"
                }
                R.id.finishToFirst -> {
                    mFinishedAction = "firstwaypoint"
                }
            }
        }

        AlertDialog.Builder(this) // creates the dialog
            .setTitle("")
            .setView(wayPointSettings)
            .setPositiveButton("Finish") { dialog, id ->
                mAltitude = altitudeEditText.text.toString().toDouble()
                mSpeed = speedEditText.text.toString().toDouble()
                Log.e(TAG, "altitude $mAltitude")
                Log.e(TAG, "speed $mSpeed")
                Log.e(TAG, "mFinishedAction $mFinishedAction")
                generate.isEnabled = true
                Toast.makeText(this, "Finished configuring mission settings", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { dialog, id -> dialog.cancel() }
            .create()
            .show()
    }
}