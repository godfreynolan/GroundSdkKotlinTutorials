package com.riis.mediamanager

/*
 *     Copyright (C) 2019 Parrot Drones SAS
 *
 *     Redistribution and use in source and binary forms, with or without
 *     modification, are permitted provided that the following conditions
 *     are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of the Parrot Company nor the names
 *       of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written
 *       permission.
 *
 *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *     "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *     LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *     FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *     PARROT COMPANY BE LIABLE FOR ANY DIRECT, INDIRECT,
 *     INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *     BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 *     OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 *     AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *     OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *     OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *     SUCH DAMAGE.
 *
 */

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.parrot.drone.groundsdk.GroundSdk
import com.parrot.drone.groundsdk.ManagedGroundSdk
import com.parrot.drone.groundsdk.Ref
import com.parrot.drone.groundsdk.device.DeviceState
import com.parrot.drone.groundsdk.device.Drone
import com.parrot.drone.groundsdk.device.RemoteControl
import com.parrot.drone.groundsdk.device.instrument.BatteryInfo
import com.parrot.drone.groundsdk.device.peripheral.MediaStore
import com.parrot.drone.groundsdk.device.peripheral.StreamServer
import com.parrot.drone.groundsdk.device.peripheral.media.MediaItem
import com.parrot.drone.groundsdk.facility.AutoConnection

class MainActivity : AppCompatActivity() {

    private val sharedViewModel: SharedViewModel by viewModels() // View Model

    private lateinit var groundSdk: GroundSdk // Parrot groundSdk instance

    // Drone:
    private var drone: Drone? = null
    private var droneStateRef: Ref<DeviceState>? = null
    private var droneBatteryInfoRef: Ref<BatteryInfo>? = null

    // Remote control:
    private var rc: RemoteControl? = null
    private var rcStateRef: Ref<DeviceState>? = null
    private var rcBatteryInfoRef: Ref<BatteryInfo>? = null

    // App Bar and Navigation
    private lateinit var appBarConfig: AppBarConfiguration
    private lateinit var navController: NavController

    // Monitoring SD Card Media
    private var mediaItemList: MediaItemList = MediaItemList()
    private var thumbnailObserverRef: Ref.Observer<Bitmap>? = null
    var mediaList: MutableList<MediaItem>? = null
    private var mediaListCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*
        creating a navigation host fragment from the FragmentContainerView
        in activity_main.xml
         */
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        /*
        setting the ToolBar in activity_main.xml as the activity's action bar
        (can now host a menu and back-press button)
         */
        val appBar = findViewById<Toolbar>(R.id.app_bar)
        setSupportActionBar(appBar)

        //setting up the appBar to work with navigation
        appBarConfig = AppBarConfiguration(
            setOf(R.id.home_dest), //setting the top-level fragment
            null
        )
        setupActionBarWithNavController(navController, appBarConfig)

        groundSdk = ManagedGroundSdk.obtainSession(this) //get a GroundSdk session

        thumbnailObserverRef = ThumbnailObserver() //initialize the thumbnail observer
    }

    //This method is called whenever the user back-presses or clicks on the "up" button in action bar.
    override fun onSupportNavigateUp(): Boolean {
        // tells the navController how to navigate to the previous fragment (if any)
        return findNavController(R.id.nav_host_fragment).navigateUp(appBarConfig)
    }

    //setting up the overflow menu from overflow_menu.xml
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.overflow_menu, menu)
        return true
    }

    override fun onStart() {
        super.onStart()

        //starting the AutoConnection facility to automatically connect the drone and its remote controller
        groundSdk.getFacility(AutoConnection::class.java) {
            it?.let{
                if (it.status != AutoConnection.Status.STARTED) {
                    it.start()
                }
                //getting the drone
                if (drone?.uid != it.drone?.uid) {
                    //if the drone changes, stop monitoring the previous drone
                    if(drone != null) {
                        stopDroneMonitors()
                        resetDroneUi()
                    }
                    drone = it.drone
                    if(drone != null) {
                        startDroneMonitors() //monitor the state changes of the new drone
                    }
                }
                //getting the remote controller
                if (rc?.uid  != it.remoteControl?.uid) {
                    //if the remote controller changes, stop monitoring the previous remote controller
                    if(rc != null) {
                        stopRcMonitors()
                        resetRcUi()
                    }
                    rc = it.remoteControl
                    if(rc != null) {
                        startRcMonitors() //monitor the state changes of the new remote controller
                    }
                }
            }
        }
    }

    private fun startDroneMonitors() {
        monitorDroneState()
        monitorDroneBatteryLevel()
        monitorMediaStoreState()
        monitorStreamServer()
    }

    private fun stopDroneMonitors() {
        droneStateRef?.close()
        droneStateRef = null

        droneBatteryInfoRef?.close()
        droneBatteryInfoRef = null

        sharedViewModel.mediaStoreRef?.close()
        sharedViewModel.mediaStoreRef = null

        thumbnailObserverRef = null

        sharedViewModel.streamServerRef?.close()
        sharedViewModel.streamServerRef = null
        sharedViewModel.streamServer = null
    }

    private fun startRcMonitors() {
        monitorRcState()
        monitorRcBatteryLevel()
    }

    private fun stopRcMonitors() {
        rcStateRef?.close()
        rcStateRef = null

        rcBatteryInfoRef?.close()
        rcBatteryInfoRef = null
    }

    private fun resetDroneUi() {
        sharedViewModel.droneState.postValue(DeviceState.ConnectionState.DISCONNECTED.toString())
        sharedViewModel.droneBattery.postValue("")
    }

    private fun resetRcUi() {
        sharedViewModel.rcState.postValue(DeviceState.ConnectionState.DISCONNECTED.toString())
        sharedViewModel.rcBattery.postValue("")
    }

    private fun monitorDroneState() {
        droneStateRef = drone?.getState {
            it?.let {
                sharedViewModel.droneState.postValue(it.connectionState.toString())
            }
        }
    }

    private fun monitorDroneBatteryLevel() {
        droneBatteryInfoRef = drone?.getInstrument(BatteryInfo::class.java) {
            it?.let {
                sharedViewModel.droneBattery.postValue(getString(R.string.percentage, it.batteryLevel))
            }
        }
    }

    private fun monitorRcState() {
        rcStateRef = rc?.getState {
            it?.let {
                sharedViewModel.rcState.postValue(it.connectionState.toString())
            }
        }
    }

    private fun monitorRcBatteryLevel() {
        rcBatteryInfoRef = rc?.getInstrument(BatteryInfo::class.java) {
            it?.let {
                sharedViewModel.rcBattery.postValue(getString(R.string.percentage, it.batteryLevel))
            }
        }
    }

    private fun monitorMediaStoreState() {
        //Get an instance of the drone's MediaStore and use it to constantly monitor the media content on the SD card
        sharedViewModel.mediaStoreRef = drone?.getPeripheral(MediaStore::class.java) { ms ->
            if (ms != null) {
                //Send the media info to the sharedViewModel so that the HomeFragment can observe it and update its UI
                sharedViewModel.indexingState.postValue(ms.indexingState.toString())
                sharedViewModel.photoMediaCount.postValue(ms.photoMediaCount.toString())
                sharedViewModel.videoMediaCount.postValue(ms.videoMediaCount.toString())

                //If the MediaStore instance exists and its media list has not yet been browsed, browse its media list
                if(sharedViewModel.mediaStoreRef != null && !sharedViewModel.observingMediaList){
                    sharedViewModel.observingMediaList = true
                    //sharedViewModel.mediaItemList.value?.mediaList?.close()

                    mediaItemList.mediaList = sharedViewModel.mediaStoreRef!!.get()?.browse { mediaList ->
                        Log.d(TAG, "List of media items refreshed")
                        //The following occurs when the contents of the media list is first loaded or changes...

                        mediaListCount = 0
                        this.mediaList = mediaList

                        //For each item in the media list, obtain its thumbnail image and send it to thumbnail observer
                        if (mediaList != null) {
                            for (media in  mediaList) {
                                thumbnailObserverRef?.let {
                                    sharedViewModel.mediaStoreRef?.get()?.fetchThumbnailOf(media,
                                        it
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun monitorStreamServer(){
        sharedViewModel.streamServerRef =
            drone?.getPeripheral(StreamServer::class.java) { streamServer ->
                streamServer?.run {
                    if (!streamingEnabled()) {
                        enableStreaming(true)
                    }
                }
                sharedViewModel.streamServer = streamServer
            }
    }

    private inner class ThumbnailObserver: Ref.Observer<Bitmap> {
        override fun onChanged(obj: Bitmap?) {
            //Each time a media item's thumbnail (Bitmap) is sent to this observer...
            if (obj != null) {
                //add the thumbnail to the mediaItemList's bitmap list
                mediaItemList.bitmapList.add(obj)
                mediaListCount++
            }

            /*
            When all of the thumbnails of the current media list on the Drone's SD card have been
            added to the mediaItemList's bitmap list, send the mediaItemList to the sharedViewModel
            so that the HomeFragment can observe it and update its UI (recycler view).
             */
            if(mediaListCount == mediaList?.size){
                sharedViewModel.mediaItemList.postValue(mediaItemList)
            }
        }
    }
}