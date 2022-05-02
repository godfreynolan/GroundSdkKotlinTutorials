# Creating a Media Manager Application
## Parrot groundsdk tutorial 3 (Kotlin)

In this tutorial, you will learn how to use several important groundsdk device peripherals, classes, and interfaces to interact with the media stored on the drone's SD card including **MediaStore**, **StreamServer**, **MediaDownloader**, **MediaDeleter**, **MediaStoreWiper**, **StreamServer** and **MediaDestination**. By the end of this tutorial, you will have an app that you can use to preview photos, play videos, download or delete files and so on. 

In order for our app to manage photos and videos, the drone must first have media existing on its SD Card. Fortunately, by using Parrot's FreeFlight 6 app or the [Camera](https://github.com/riisinterns/GroundSdkKotlinTutorials/tree/main/Camera) groundsdk tutorial app, you can easily capture media on the drone.

You can download this tutorial's final sample project from this [Github Page](https://github.com/riisinterns/drone-lab-four-media-manager).

---
## Preparation
Throughout this tutorial we will be using Android Studio Bumblebee | 2021.1.1. You can download the latest version of Android Studio from here: http://developer.android.com/sdk/index.html.

> Note: In this tutorial, we will use a Parrot ANAFI 4k drone for testing. However, other models that use Parrot's groundsdk should be capable of working with this code. A micro SD card must be inserted into the drone for this application to work.

---
## Setting up the Application

### 1. Create the project

*   Open Android Studio and in the start-up screen select **File -> New Project**

*   In the **New Project** screen:
    *   Set the device to **"Phone and Tablet"**.
    *   Set the template to **"Empty Activity"** and then press **"Next"**.

*   On the next screen:
    * Set the **Application name** to your desired app name. In this example we will use `MediaManager`.
    * The **Package name** is conventionally set to something like "com.companyName.applicationName". We will use `com.riis.mediamanager`.
    * Set **Language** to `Kotlin`
    * Set **Minimum SDK** to `API 21: Android 5.0 (Lollipop)`
    * Do **NOT** check the option to "Use legacy android.support.libraries"
    * Click **Finish** to create the project.

### 2. Gradle dependencies

*   Open up the app-level gradle file (**Gradle Scripts** -> **build.gradle:app**). In **defaultConfig**, set **minSdk** to `24`. This is the mimumum SDK version that is supported by Parrot's groundsdk

```kotlin
defaultConfig {
    applicationId "com.riis.mediamanager"
    // Set the minimum SDK version supported by GroundSdk
    minSdk 24
    targetSdk 32
    versionCode 1
    versionName "1.0"

    testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
}
```

*   In **dependencies**, ensure the following lines of code are there:

```kotlin
dependencies {
    //navigation
    implementation 'androidx.navigation:navigation-fragment-ktx:2.4.2'
    implementation 'androidx.navigation:navigation-ui-ktx:2.4.2'
    
    //Android
    implementation 'androidx.core:core-ktx:1.7.0'
    implementation 'androidx.appcompat:appcompat:1.4.1'
    implementation 'com.google.android.material:material:1.5.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.3'
    implementation 'androidx.navigation:navigation-fragment-ktx:2.4.2'
    implementation 'androidx.navigation:navigation-ui-ktx:2.4.2'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.3'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.4.0'

    // GroundSdk dependencies
    implementation 'com.parrot.drone.groundsdk:groundsdk:7.0.1'
    runtimeOnly 'com.parrot.drone.groundsdk:arsdkengine:7.0.1'
    
    //ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.2.0'
}
```
---
## Implementing the MainActivity Class

The **MainActivity.kt** file is created by Android Studio by default. We will use it to manage the connection to the drone and its remote controller, to monitor the media content of the drone's SD card, and to create a streaming server for replaying SD card videos.

The MainActivity will also be used to host two different fragments: **HomeFragment** and **MediaFragment**. It will use a **SharedViewModel** to share its information to these fragments.

Let's start with defining our class variables and creating the activity views. Replace the code in the **MainActivity.kt** file with the following:
```kotlin
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
}
```

In the **onCreate()** function, we have done the following:
*   Created an instance of the **SharedViewModel** class, `sharedViewModel`.
*   The activity's view was set to the layout from **activity_main.xml**

*   created a host fragment, `navHostFragment`, from the activity's layout. This host fragment is connected to a navigation graph, **nav_graph.xml**, which is used to define the fragments and their navigation actions.

*   from the host fragment, we have also created a navigation controller, `navController`, which is used by the activity to control navigation to different fragments.

*   the activity's app bar was created using a ToolBar from the activity's layout and then configured to work with `navController`

*   obtained a session, `groundsdk`, from Parrot's GroundSdk class which will allow us to interact with the drone.

*   initialized the thumbnail observer, `thumbnailObserverRef`, from the custom **ThumbnailObserver** class which we will soon create.

In the **onSupportNavigateUp()** function:
*   we have configured the `navcontroller` to navigate to the previously hosted fragment (if there is one) whenever the user back-presses or clicks on the "up" button in app bar.

In the **onCreateOptionsMenu()** function:
*   we have created an overflow menu in the activity's app bar from **overflow_menu.xml**. The menu displays two options: "Download All" and "Delete All". We will define what happens when each of these options are selected in the **HomeFragment**.

Next let's add the **onStart()** function to start the activity:
```kotlin
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
                //if the remote controller changes, stop monitoring the remote controller
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
```
In the above code, we have started the drone's **AutoConnection** facility which will try to automatically connect to the the drone and its remote controller. Whenever a new drone or remote controller has been connected, we will begin to monitor their states and initialize their related observers using the **startDroneMonitors()** and the **startRcMonitors()** functions.

If the drone or controller ever changes, we will stop monitoring its previous instance using the **stopDroneMonitors()** and **stopRcMonitors()** functions to close their related observers. Furthermore, we will reset any UI relating to the drone and controller states using the the **resetDroneUi()** and **resetRcUi()** functions.

Now let's add the above mentioned functions to the code:
```kotlin
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
```
In the **startDroneMonitors()** function:
*   We begin monitoring the connection state and battery level of the drone using the **monitorDroneState()** and **monitorDroneBatteryLevel()** functions. 
*   We begin monitoring the media content on the drone's SD card using the **monitorMediaStoreState()** function.
*   we are creating a streaming server which will be used for video playback using the **monitorStreamServer()** function.

Similarly in the **startRcMonitors()** function, we are monitoring the connection state and battery level of the remote controller using the **monitorRcState()** and **monitorRcBatteryLevel()** functions.

Now let's add the connection state and battery level monitor functions to the code:
```kotlin
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
```
In each of the above functons, we are monitoring a connection state or battery level from either the drone or the remote controller, and sending its value to the `sharedViewModel` whenever the value is updated. The **HomeFragment** will observe these values and use them to update its UI.

Next let's add the **monitorMediaStoreState()** function to the code:
```kotlin
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
```
In the above code, we are getting an instance of the drone's **MediaStore** peripheral which we then use to monitor the media content on the drone's SD card. Each time there is a change to the media contents of the SD card, the indexing state, number of photos, and number of videos on the SD card will be sent to `sharedViewModel`. The **HomeFragment** will observe these values and use them to update its UI.

Furthermore, upon each MediaStore updtate, a list of the media items from the drone's SD card is obtained and saved to `mediaItemList`. We then use the **fetchThumbnailOf()** function to obtain the thumbnail image of each media item in the media list as a Bitmap and send it to `thumbnailObserverRef`. The thumbnailObserverRef will then send these images to `sharedViewModel` where **HomeFragment** will be able to observe the images and use them to update its recycler view UI.

Next let's add the **monitorStreamServer()** function to the code:
```kotlin
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
```
In the above code, we are getting an instance of the drone's **StreamServer** peripheral which we then use to create a stream server and save it in `sharedViewModel`. The **MediaFragment** will later use this stream server to replay videos from the drone's SD card.

Finally, let's define the **ThumbnailObserver** class used ealier to initialize `thumbnailObserverRef`. Add the following to the code:
```kotlin
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
```
The **ThumbnailObserver** class is used to recieve thumbnail images of each of the media items in the drone's SD card whenever the SD card is updated. Each time a thumbnail is recieved, it is added to the bitmap list in `mediaItemList`. When all of the thumbnails belonging to the current media list have been added, `mediaItemList` is sent to `sharedViewModel` where **HomeFragment** will observe it and use it to update its recycler view UI.

---
## Implementing the MainActivity Layout

The **activity_main.xml** file is created by Android Studio by default. Replace the code in it with the following:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity"
    android:orientation="vertical">

    <androidx.appcompat.widget.Toolbar
        android:id="@+id/app_bar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="@color/purple_500"
        android:elevation="4dp"
        android:theme="@style/ThemeOverlay.MaterialComponents.Dark.ActionBar" />

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/nav_host_fragment"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        app:defaultNavHost="true"
        app:navGraph="@navigation/nav_graph">

    </androidx.fragment.app.FragmentContainerView>

</LinearLayout>
```

---
## Implementing the Overflow Menu Layout
Under the **res/layout** directory, create a new xml file and name it **overflow_menu.xml**. Replace the xml code with the following:
```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">

    <item
        android:id="@+id/download_all_menu_item"
        android:title="@string/download_all" />

    <item
        android:id="@+id/delete_all_menu_item"
        android:title="@string/delete_all" />
</menu>
```
This resource is used by **MainActivity** to inflate an overflow menu in its app bar. It has two menu options: "Download All" and "Delete All". The actions associated with selecting either option are defined within the **onOptionsItemSelected()** function in **HomeFragment**.

---
## Implementing the SharedViewModel Class
Create a new Kotlin file and name it **SharedViewModel.kt**. Replace the code in the file with the following:
```kotlin
class SharedViewModel: ViewModel() {
    var mediaStoreRef: Ref<MediaStore>? = null

    var streamServerRef: Ref<StreamServer>? = null
    var streamServer: StreamServer? = null

    var droneState: MutableLiveData<String> =
        MutableLiveData(DeviceState.ConnectionState.DISCONNECTED.toString())
    var droneBattery: MutableLiveData<String> = MutableLiveData("%")
    var rcState: MutableLiveData<String> =
        MutableLiveData(DeviceState.ConnectionState.DISCONNECTED.toString())
    var rcBattery: MutableLiveData<String> = MutableLiveData("%")

    var indexingState: MutableLiveData<String> = MutableLiveData(MediaStore.IndexingState.UNAVAILABLE.toString())
    var photoMediaCount: MutableLiveData<String> = MutableLiveData("n/a")
    var videoMediaCount: MutableLiveData<String> = MutableLiveData("n/a")

    var observingMediaList = false
    var mediaItemList: MutableLiveData<MediaItemList> = MutableLiveData<MediaItemList>()

    var selectedMedia: MediaItem? = null
    var selectedMediaType:MutableLiveData<String> = MutableLiveData("n/a")
    var selectedMediaPosition: Int = 0
}

data class MediaItemList(
    var mediaList: Ref<MutableList<MediaItem>>? = null, var bitmapList: MutableList<Bitmap> = mutableListOf()
)
```
The **SharedViewModel** is used to share information from **MainActivity** to **HomeFragment** and **MediaFragment**. This information includes drone and remote controller states, SD card state and its media content, and a streaming server.

---
## Implementing the HomeFragment Class
Create a new Kotlin class and name it **HomeFragment.kt**. This fragment will be used to display the drone and remote controller states as well as the drone's SD card state and media content. All this information is provided by **MainActivity** and shared via **SharedViewModel**. 

This fragment contains a recycler view to display the current list of the media items (and their thumbnails) from the drone's SD card. The user is able to select a certain media item from the list and then either download the media to the mobile device, delete the media from the SD Card, or replay the media if it is a video. If the user chooses to replay a video, the `navController` of **MainActivity** will navigate to **MediaFragment** where the video will be played.

Finally, the user can access the overflow menu in the app bar and select "Download All" to download all media items from the SD card to the mobile device, or "Delete All" to delete all media items on the SD card.

Let's start with defining our class variables and creating the fragment views. Replace the code in **HomeFragment.kt** with the following:
```kotlin
const val TAG = "HomeFragment"

class HomeFragment: Fragment() {

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var mediaDestinationRef: MediaDestination? = null

    private var mediaDownloaderRef: Ref.Observer<MediaDownloader>? = null

    private var mediaStoreWiperRef: Ref.Observer<MediaStoreWiper>? = null

    private var mediaDeleterRef: Ref.Observer<MediaDeleter>? = null

    // Text Views:
    private lateinit var droneStateTxt: TextView
    private lateinit var droneBatteryTxt: TextView
    private lateinit var rcStateTxt: TextView
    private lateinit var rcBatteryTxt: TextView
    private lateinit var indexingTxt: TextView
    private lateinit var photoMediaCountTxt: TextView
    private lateinit var videoMediaCountTxt: TextView

    // Pop-ups
    private lateinit var downloadTxt: TextView
    private lateinit var missingMediaTxt: TextView

    // Buttons
    private lateinit var deleteMediaBt: Button
    private lateinit var downloadMediaBt: Button
    private lateinit var viewMediaBt: Button

    // Recycler View
    private lateinit var fileRecyclerView: RecyclerView
    private var fileAdapter: FileListAdapter? = null

    // Progress Bar
    private lateinit var progressBar: ProgressBar
    private lateinit var progressLayout: FrameLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Media Downloading
        mediaDownloaderRef = Downloader()
        mediaDestinationRef = MediaDestination.appPrivateFiles("PARROT MEDIA")

        // Media Deleting
        mediaDeleterRef = Deleter()
        mediaStoreWiperRef = Wiper()

        // Text Views
        droneStateTxt = view.findViewById(R.id.droneStateTxt)
        droneBatteryTxt = view.findViewById(R.id.droneBatteryTxt)
        rcStateTxt = view.findViewById(R.id.rcStateTxt)
        rcBatteryTxt = view.findViewById(R.id.rcBatteryTxt)
        indexingTxt = view.findViewById(R.id.indexStateTxt)
        photoMediaCountTxt = view.findViewById(R.id.photoMediaCountTxt)
        videoMediaCountTxt = view.findViewById(R.id.videoMediaCountTxt)
        downloadTxt = view.findViewById(R.id.downloadTextView)
        missingMediaTxt = view.findViewById(R.id.missingMediaTextView)

        // Buttons
        downloadMediaBt = view.findViewById(R.id.downloadMediaBt)
        deleteMediaBt = view.findViewById(R.id.deleteMediaBt)
        viewMediaBt = view.findViewById(R.id.viewMediaBt)

        // Recycler View
        fileRecyclerView = view.findViewById(R.id.file_recycler_view)
        fileRecyclerView.layoutManager = LinearLayoutManager(context)
        fileAdapter = FileListAdapter()
        fileRecyclerView.adapter = fileAdapter

        // Progress Bar
        progressBar = view.findViewById(R.id.progressBar)
        progressLayout = view.findViewById(R.id.progressLayout)


        downloadMediaBt.setOnClickListener {
            if(sharedViewModel.selectedMedia != null){
                Log.d(TAG, "Downloading media item $sharedViewModel.{selectedMedia!!.name}...")
                downloadMedia(sharedViewModel.selectedMedia?.resources as MutableList<MediaItem.Resource>)
            }
        }
        
        deleteMediaBt.setOnClickListener {
            if(sharedViewModel.selectedMedia != null) {
                Log.d(TAG, "Deleting media item ${sharedViewModel.selectedMedia!!.name}...")
                deleteMedia(sharedViewModel.selectedMedia?.resources as MutableList<MediaItem.Resource>)
            }
        }

        viewMediaBt.setOnClickListener {
            findNavController().navigate(R.id.moveToDetailView)
        }

        setHasOptionsMenu(true)

        return view
    }
```
In the above code we have done the following:
*   created an instance of the **SharedViewModel** class, `sharedViewModel`.
*   the fragment's view was set to the layout from **fragment_home.xml**
*   important media action observers were initialized including `mediaDownloaderRef`, `mediaDeleterRef`, and `mediaStoreWiperRef`
*   The `mediaDestinationRef` variable specifies that any downloaded media will be saved to a directory called "PARROT MEDIA" in the app's private directory on the mobile device
*   text views were created to display the different states of the drone and its remote controller, display the number of photos and videos on the drone's SD card, display media downloading progress, and to notify when the SD card is empty
*   Three buttons were created for interacting with individual media items. Pressing `downloadMediaBt` calls the **downloadMedia()** function , pressing `deleteMediaBt` calls the **deleteMedia()** function, and pressing `viewMediaBt` results in the **MainActivity** navigating to **MediaFragment**
*   A progress bar was created to display media downloading progress

Now let's implement the **onViewCreated()** function by adding the following code:
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Drone State
    sharedViewModel.droneState.observe(viewLifecycleOwner) { droneState ->
        Log.d(TAG, droneState)
        if (droneState == DeviceState.ConnectionState.DISCONNECTED.toString()) {
            Log.d(TAG, "cool")
            downloadMediaBt.isEnabled = false
            deleteMediaBt.isEnabled = false
            viewMediaBt.isEnabled = false
            photoMediaCountTxt.text = "n/a"
            videoMediaCountTxt.text = "n/a"
            droneBatteryTxt.text = "%"
            missingMediaTxt.text = "There is no Drone Connected"
            missingMediaTxt.visibility = View.VISIBLE
            fileRecyclerView.visibility = View.GONE
        }
        else if(droneState == DeviceState.ConnectionState.CONNECTED.toString() && checkMediaMissing()){
            downloadMediaBt.isEnabled = false
            deleteMediaBt.isEnabled = false
            viewMediaBt.isEnabled = false
            missingMediaTxt.text =
                "There is currently no media available on the drone's SD card"
            missingMediaTxt.visibility = View.VISIBLE
        }
        else{
            downloadMediaBt.isEnabled = true
            deleteMediaBt.isEnabled = true
            viewMediaBt.isEnabled = true
            missingMediaTxt.visibility = View.GONE
            fileRecyclerView.visibility = View.VISIBLE
        }

        droneStateTxt.text = droneState
    }
    // Drone Battery
    sharedViewModel.droneBattery.observe(viewLifecycleOwner){ droneBattery ->
        droneBatteryTxt.text = droneBattery
    }
    // RC State
    sharedViewModel.rcState.observe(viewLifecycleOwner){ rcState ->
        if (rcState == DeviceState.ConnectionState.DISCONNECTED.toString()) {
            rcBatteryTxt.text = "%"
        }
        rcStateTxt.text = rcState
    }
    // RC Battery
    sharedViewModel.rcBattery.observe(viewLifecycleOwner){ rcBattery ->
        rcBatteryTxt.text = rcBattery
    }
    // Indexing State
    sharedViewModel.indexingState.observe(viewLifecycleOwner){ indexingState ->
        indexingTxt.text = indexingState
    }
    // Photo Media Count
    sharedViewModel.photoMediaCount.observe(viewLifecycleOwner){ photoMediaCount ->
        Log.d(TAG, photoMediaCount)
        photoMediaCountTxt.text = photoMediaCount
    }
    // Video Media Count
    sharedViewModel.videoMediaCount.observe(viewLifecycleOwner){ videoMediaCount ->
        Log.d(TAG, videoMediaCount)
        videoMediaCountTxt.text = videoMediaCount
    }
    // Refresh Recycler View when SD Card Media Changes
    sharedViewModel.mediaItemList.observe(viewLifecycleOwner){
        fileAdapter = FileListAdapter()
        fileRecyclerView.adapter = fileAdapter
    }
    // Media type of currently selected media item
    sharedViewModel.selectedMediaType.observe(viewLifecycleOwner){ mediaType ->
        viewMediaBt.isEnabled = mediaType == "VIDEO"
    }
}
```
In the above function, we are accessing the `sharedViewModel` and observing values that are being posted from **MainActivity**. Each time the **MainActivity** updates one its values in the `sharedViewModel`, the **HomeFragment** will take the updated value and use it to update its UI. The UI updates can be described as the following:
*   observing `sharedViewModel.droneState`:
    * if the drone is disconnected, disable the buttons, hide the recycler view, and notify the user using the `missingMediaTxt` text view
    * if the drone is connected and media does not exist on its SD card (**checkMediaMissing()** == true), disable the buttons and notify the user that media is missing using the `missingMediaTxt` text view
    * if the drone is connected and media exists on its SD card, show the recycler view and enable the buttons
*   observing `sharedViewModel.mediaItemList`:
    * whenever the media list for the drone's SD card changes, update the recycler view by setting `fileAdapter` to a new **FileListAdapter()** object containing the new media list
*   observing `sharedViewModel.selectedMediaType`:
    * If the current media type is a video, enable `viewMediaBt`. Otherwise disable it.
* The remaining observers update their respective text views in **HomeFragment** whenever they recieve an update in `sharedViewModel`

Next let's add the following functions to the code:
```kotlin
private fun checkMediaMissing(): Boolean {
    return sharedViewModel.photoMediaCount.value == "0" && sharedViewModel.videoMediaCount.value == "0"
}

private fun downloadMedia(resources: MutableList<MediaItem.Resource>){
    mediaDestinationRef?.let {
        mediaDownloaderRef?.let { it1 ->
            sharedViewModel.mediaStoreRef?.get()?.download(resources,
                it, it1
            )
        }
    }
}

private fun deleteMedia(resources: MutableList<MediaItem.Resource>){
    mediaDeleterRef?.let { sharedViewModel.mediaStoreRef?.get()?.delete(resources, it) }
}

override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.download_all_menu_item -> {
            Log.d(TAG, "Downloading all media items...")
            downloadAllMedia()
            true
        }
        R.id.delete_all_menu_item -> {
            Log.d(TAG, "Clearing the SD card...")
            wipeMedia()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}

private fun downloadAllMedia(){
    Log.d(TAG2, "donwloadAllMedia()")
    val resources = mutableListOf<MediaItem.Resource>()
    for (mediaItem in sharedViewModel.mediaItemList.value?.mediaList?.get()!!){
        mediaItem.let { resources.addAll(it.resources) }
    }
    downloadMedia(resources)
}

private fun wipeMedia(){
    mediaStoreWiperRef?.let { sharedViewModel.mediaStoreRef?.get()?.wipe(it) }
}
```
The **checkMediaMising()** function indicates whether media exists on the drone's SD card by checking that the sum of `sharedViewModel.photoMediaCount` and `sharedViewModel.videoMediaCount` is greater than zero.

The **downloadMedia()** function downloads the currently selected media item in the recycler view using the **MediaStore** peripheral. As the media item is downloading, progress updates are sent to the `mediaDownloaderRef` observer (created from the **Downloader()** class).

The **deleteMedia()** function deletes the currently selected media item in the recycler view from the drone's SD card. This will subsequently remove it from the recycler view as well. Deletion updates are sent to the `mediaDeleteRef` observer (created from the **Deleter()** class).

The **onOptionsItemSelected()** function attaches the menu options from the app bar's overflow menu to its associated functions. The "Download All" option calls the **downloadAllMedia()** function and the "Delete All" option calles the **wipeMedia()** function.

The **downloadAllMedia()** function downloads all of the media items in the recycler view by passing each item into the **downloadMedia()** function.

The **wipeMedia()** function wipes all media items in the drone's SD card using the **MediaStore** peripheral. Wiping updates are sent to the `mediaStoreWiperRef` observer (created from the **Wiper()** class).

Next let's add the following custom observer classes to the code:
```kotlin
inner class Downloader: Ref.Observer<MediaDownloader>{
    override fun onChanged(obj: MediaDownloader?) {
        if (obj != null) {
            progressLayout.visibility = View.VISIBLE
            progressBar.progress = obj.totalProgress
            downloadTxt.text = "Downloading ... ${obj.totalProgress}%"

            if(obj.status == MediaTaskStatus.FILE_PROCESSED) {
                val file = File(obj.downloadedFile?.path.toString())
                val fileSize = ((file.length() / 1024).toInt()).toString() 

                Log.d(TAG, "Media File ${obj.downloadedFile?.name} ($fileSize MB) has been downloaded")
            }
            if(obj.status == MediaTaskStatus.COMPLETE) {
                progressBar.progress = 0
                progressLayout.visibility = View.GONE
            }
        }
    }
}

inner class Deleter: Ref.Observer<MediaDeleter>{
    override fun onChanged(obj: MediaDeleter?) {
        Log.d(TAG, "Deleter()")
        if (obj != null) {
            if(obj.status == MediaTaskStatus.COMPLETE){
                Log.d(TAG, "Media item has been deleted")
            }
        }
    }
}

inner class Wiper: Ref.Observer<MediaStoreWiper>{
    override fun onChanged(obj: MediaStoreWiper?) {
        if (obj != null) {
            if(obj.status == MediaTaskStatus.COMPLETE){
                fileRecyclerView.visibility = View.GONE
                missingMediaTxt.text =
                    "There is currently no media available on the drone's SD card"
                missingMediaTxt.visibility = View.VISIBLE
            }
            Log.d(TAG,"Wiper: ${obj.status}")
        }
    }
}
```
The **Downloader()** observer recieves updates whenever a media item is being downloaded from the drone's SD card. These updates are used to display the current download progress using `progressBar` and `downloadTxt`.

The **Deleter()** observer recieves updates whenever a media item is being deleted from the drone's SD card. There are no UI updates when this happens.

The **Wiper()** observer recieves updates when all of the media on the drone's SD card is being wiped. There are no UI updates when this happens.

Next let's create the adapter class for the recycler view by adding the following code:
```kotlin
private inner class FileListAdapter() : RecyclerView.Adapter<FileListAdapter.ItemHolder>() {
    var selectedPosition = 0

    override fun getItemCount(): Int {
        return sharedViewModel.mediaItemList.value?.mediaList?.get()?.size ?: 0
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_recycler_view_dialog, parent, false)
        return ItemHolder(view)
    }

    override fun onBindViewHolder(mItemHolder: ItemHolder, index: Int) {
        if (selectedPosition == index){
            if (sharedViewModel.selectedMedia != sharedViewModel.mediaItemList.value?.mediaList?.get()?.get(index)){
                sharedViewModel.selectedMedia = sharedViewModel.mediaItemList.value?.mediaList?.get()?.get(index)

                sharedViewModel.selectedMediaType.postValue(sharedViewModel.mediaItemList.value?.mediaList?.get()?.get(index)?.type.toString())
                Log.d(TAG, "Currently selected media item: ${sharedViewModel.selectedMedia?.name}\n")
            }
            mItemHolder.itemView.setBackgroundColor(resources.getColor(R.color.purplePink))

        }else{
            mItemHolder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
        val file: MediaItem? = sharedViewModel.mediaItemList.value?.mediaList?.get()?.get(index)
        val fileName = file?.name
        val fileType = file?.type
        val fileSize = file?.resources?.get(0)?.let { Formatter.formatShortFileSize(context, it.size) }
        val videoDuration = file?.resources?.get(0)?.duration?.let {
            TimeUnit.MILLISECONDS.toSeconds(
                it
            )
        }?.let { DateUtils.formatElapsedTime(it) }
        if(sharedViewModel.mediaItemList.value?.bitmapList?.size!! > index){
            val bitmap = sharedViewModel.mediaItemList.value?.bitmapList?.get(index)
            if (file != null) {
                mItemHolder.fileImageView.setImageBitmap(bitmap)
            }
        }
        mItemHolder.fileNameTextView.text = "File Name: $fileName"
        mItemHolder.fileTypeTextView.text = "File Type: $fileType"
        mItemHolder.fileSizeTextView.text = "File Size: $fileSize"
        mItemHolder.itemView.tag = index

        if(sharedViewModel.mediaItemList.value?.mediaList?.get()?.get(index)?.type == MediaItem.Type.VIDEO){
            mItemHolder.videoDurationTextView.visibility = View.VISIBLE
            mItemHolder.videoDurationTextView.text = "Video Duration: $videoDuration"
        }
        else{
            mItemHolder.videoDurationTextView.visibility = View.INVISIBLE
        }
    }

    inner class ItemHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }
        var fileNameTextView: TextView = itemView.findViewById(R.id.file_name_text_view)
        var fileTypeTextView: TextView = itemView.findViewById(R.id.file_type_text_view)
        var fileSizeTextView: TextView = itemView.findViewById(R.id.file_size_text_view)
        var videoDurationTextView: TextView = itemView.findViewById(R.id.video_duration_text_view)
        var fileImageView: ImageView = itemView.findViewById(R.id.file_image_view)

        override fun onClick(itemView: View) {
            if (adapterPosition == RecyclerView.NO_POSITION) return

            notifyItemChanged(selectedPosition)
            selectedPosition = adapterPosition
            notifyItemChanged(selectedPosition)
            sharedViewModel.selectedMediaPosition = selectedPosition
        }
    }
}
```
The **FileListAdapter()** uses the media items and their thumbnail images from the `sharedViewModel.mediaItemList` to populate the recycler view. The ItemHolder for the adapter has its view inflated from **layout.list_item_recycler_view_dialog.xml**

Finally, let's add the fragment's **onDestroy()** function to the code:
```kotlin
override fun onDestroyView() {
    mediaDeleterRef = null
    mediaDestinationRef = null
    mediaDownloaderRef = null
    mediaStoreWiperRef = null

    super.onDestroyView()
}
```
In the above code, we are reseting all of our media observers whenever the fragment is about to be destroyed.

Finally, let's create the layout for each recycler view item. Create a new xml file in **res/layout** and name it **list_item_recycler_view_dialog.xml**. Replace the xml code with the following:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/file_list_item_linear_layout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:layout_gravity="center"
    android:layout_marginVertical="8dp">

    <ImageView
        android:id="@+id/file_image_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:src="@drawable/baseline_perm_media_black_24"
        android:layout_marginLeft="12dp"
        android:layout_marginRight="30dp">

    </ImageView>

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:id="@+id/file_name_text_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="">
        </TextView>

        <TextView
            android:id="@+id/file_type_text_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="">
        </TextView>

        <TextView
            android:id="@+id/file_size_text_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="">
        </TextView>

        <TextView
            android:id="@+id/video_duration_text_view"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:visibility="invisible"
            android:text="">
        </TextView>

    </LinearLayout>
    
</LinearLayout>
```

---
## Implementing the HomeFragment Layout

Create a new xml file in the **res/layout** directory and name it **fragment_home.xml**. Replace the xml code with following:
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:id="@+id/info"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        app:layout_constraintTop_toTopOf="parent"
        android:layout_marginBottom="12dp"
        android:padding="12dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/labelDroneControl"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text="Drone" />

            <TextView
                android:id="@+id/droneStateTxt"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="2"
                android:text="state"
                android:textAlignment="center" />

            <TextView
                android:id="@+id/droneBatteryTxt"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text=""
                android:textAlignment="textEnd" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/labelRemoteControl"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text="Remote" />

            <TextView
                android:id="@+id/rcStateTxt"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="2"
                android:text="state"
                android:textAlignment="center" />

            <TextView
                android:id="@+id/rcBatteryTxt"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text=""
                android:textAlignment="textEnd" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/labelIndexingState"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text="Index State" />

            <TextView
                android:id="@+id/indexStateTxt"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text=""
                android:textAlignment="center" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/labelPhotoMediaCount"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text="Photo Media Count" />

            <TextView
                android:id="@+id/photoMediaCountTxt"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text=""
                android:textAlignment="center" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/labelVideoMediaCount"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text="Video Media Count" />

            <TextView
                android:id="@+id/videoMediaCountTxt"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:text=""
                android:textAlignment="center" />
        </LinearLayout>


    </LinearLayout>


    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/file_recycler_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:scrollbars="vertical"
        app:layout_constraintBottom_toTopOf="@+id/downloadMediaBt"
        app:layout_constraintTop_toBottomOf="@+id/info"
        tools:layout_editor_absoluteX="-26dp">

    </androidx.recyclerview.widget.RecyclerView>

    <Button
        android:id="@+id/downloadMediaBt"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="12dp"
        android:layout_marginBottom="4dp"
        android:text="@string/download_media"
        app:layout_constraintBottom_toTopOf="@+id/viewMediaBt"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintHorizontal_bias="1.0"
        app:layout_constraintStart_toStartOf="parent" />

    <Button
        android:id="@+id/viewMediaBt"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="12dp"
        android:layout_marginBottom="4dp"
        android:text="@string/view_media"
        app:layout_constraintBottom_toTopOf="@id/deleteMediaBt"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <Button
        android:id="@+id/deleteMediaBt"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="12dp"
        android:layout_marginBottom="4dp"
        android:text="@string/delete_media"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <TextView
        android:id="@+id/missingMediaTextView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="12dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:text="@string/no_media_prompt"
        android:gravity="center"
        android:textStyle="bold"
        android:visibility="gone"
        android:textSize="20dp"/>

    <FrameLayout
        android:id="@+id/progressLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:visibility="gone"
        android:layout_marginHorizontal="12dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        android:background="@color/black">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <ProgressBar
                android:id="@+id/progressBar"
                android:layout_margin="30dp"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"
                android:max="100"
                android:indeterminate="false"
                android:progress="0"
                style="@style/Widget.AppCompat.ProgressBar.Horizontal"
                android:progressDrawable="@drawable/progress"/>

            <TextView
                android:id="@+id/downloadTextView"
                android:layout_margin="12dp"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textColor="@color/white"
                android:text=""
                android:textSize="18dp"
                app:layout_constraintTop_toBottomOf="@id/progressBar"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent"
                />

        </androidx.constraintlayout.widget.ConstraintLayout>

    </FrameLayout>


</androidx.constraintlayout.widget.ConstraintLayout>
```

---
## Implementing the MediaFragment Class

Create a new Kotlin class and name it **MediaFragment.kt**. This fragment will be used to view and replay videos from the drone's SD card on a **StreamView**. This fragment also provides an interface for the user to play and pause videos.

Let's start with defining our class variables and creating the fragment views. Replace the code in MediaFragment.kt with the following:

```kotlin
class MediaFragment: Fragment() {
    private val sharedViewModel: SharedViewModel by activityViewModels()

    // Video Stream
    private var stream: Ref<MediaReplay>? = null
    private lateinit var streamView: GsdkStreamView
    private var mediaReplayObserver: Ref.Observer<MediaReplay>? = null

    // Selected Video
    private var videoResource:  MediaReplay.Source? = null

    // Video Playback Interface
    private var mPlayPauseBtn: ImageButton? = null
    private var mSeekBar: SeekBar? = null
    private var mPositionText: TextView? = null
    private var mDurationText: TextView? = null
    private var mProgressHandler: Handler? = null
    private val refreshInterval = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //inflating the fragment view
        val view = inflater.inflate(R.layout.fragment_media, container, false)

        // Video Playback Interface
        streamView = view.findViewById(R.id.stream_view)
        mPlayPauseBtn = view.findViewById(R.id.play_pause_btn)
        mSeekBar = view.findViewById(R.id.seek_bar)
        mPositionText = view.findViewById(R.id.position_text)
        mDurationText = view.findViewById(R.id.duration_text)
        mProgressHandler = Handler()

        mediaReplayObserver = MediaReplayObserver() //observes when a video is being replayed

        return view
    }
}
```
Next let's add the rest of the code to the fragment:
```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /*
            If the stream server exists and the currently selected media is a video,
            create a video resource from it and replay it.
         */
        if(sharedViewModel.streamServer != null){
            if(sharedViewModel.selectedMedia?.type == MediaItem.Type.VIDEO) {
                videoResource = sharedViewModel.selectedMedia?.resources?.get(0)
                    ?.let { MediaReplay.videoTrackOf(it, MediaItem.Track.DEFAULT_VIDEO) }
            }
            //Replaying a video creates a stream. MediaReplayObserver is used to update the UI when the stream changes.
            mediaReplayObserver?.let {
                stream = sharedViewModel.streamServer!!.replay(videoResource!!, it)
            }
        }
    }

    override fun onDestroy() {
        streamView.setStream(null)
        stream?.close()

        super.onDestroy()
    }

    private fun updateStream(stream: Replay) {
        //update the stream view with the stream being replayed
        streamView.setStream(stream)

        val state = stream.state()
        val playState = stream.playState()
        val duration = stream.duration()

        //If the stream is stopped, change it to paused.
        //If the stream is running and goes past the video duration, stop it.
        if (state == Stream.State.STOPPED) {
            stream.pause()
        } else if (state == Stream.State.STARTED && stream.position() >= duration) {
            stream.stop()
        }
        //If the stream is running, show the pause button. Otherwise, show the play button.
        mPlayPauseBtn?.setImageResource(if (playState == Replay.PlayState.PLAYING) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)

        //When the PlayPause button is pressed... pause the stream if it is playing and vice versa.
        mPlayPauseBtn?.setOnClickListener {
            if (playState == Replay.PlayState.PLAYING) {
                stream.pause()
            } else {
                stream.play()
            }
        }
        //show the video's duration
        mDurationText?.let { setTime(it, duration) }

        //Updates the UI based on the stream's current playback state
        val progressUpdate: Runnable = object : Runnable {
            override fun run() {
                //show the position of the stream progress
                val position = stream.position().toInt()
                mSeekBar!!.progress = position
                setTime(mPositionText!!, position.toLong())

                //delay the next update by the refresh interval (100 ms)
                if (playState == Replay.PlayState.PLAYING) {
                    mProgressHandler!!.postDelayed(this, refreshInterval.toLong())
                }
            }
        }

        mSeekBar!!.isEnabled = duration > 0 //enable the seek bar if the video has a duration
        mSeekBar!!.max = duration.toInt() //fit the seek bar to the video duration
        mSeekBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                //If the user changes the seek bar position, show the new position
                if (fromUser) {
                    setTime(mPositionText!!, progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                //When the seek bar is touched, stop any UI updates
                mProgressHandler!!.removeCallbacksAndMessages(null)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                //After the seek bar has been touched, send the stream position to the seek bar position
                stream.seekTo(seekBar.progress.toLong())
            }
        })

        mProgressHandler?.removeCallbacksAndMessages(null)
        progressUpdate.run() //run the progress ui updates

    }

    private fun setTime(view: TextView, timeMillis: Long) {
        view.text = DateUtils.formatElapsedTime(TimeUnit.MILLISECONDS.toSeconds(timeMillis))
    }

    inner class MediaReplayObserver: Ref.Observer<MediaReplay>{
        override fun onChanged(stream: MediaReplay?) {
            if (stream != null) {
                updateStream(stream)
            }
        }
    }

}
```

---
## Implementing the MediaFragment Layout

Create a new xml file in the **res/layout** directory and name it **fragment_media.xml**. Replace the xml code with following:
```xml
<?xml version="1.0" encoding="utf-8"?>

<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:layout_marginBottom="8dp">

    <com.parrot.drone.groundsdk.stream.GsdkStreamView
        android:id="@+id/stream_view"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginBottom="8dp"
        app:layout_constraintBottom_toTopOf="@id/play_pause_btn"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"/>

    <ImageButton
        android:id="@+id/play_pause_btn"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:background="?attr/selectableItemBackground"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        tools:src="@android:drawable/ic_media_play"
        app:tint="@color/color_accent" />

    <TextView
        android:id="@+id/position_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        app:layout_constraintBottom_toBottomOf="@id/seek_bar"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintEnd_toStartOf="@id/seek_bar"
        app:layout_constraintStart_toEndOf="@id/play_pause_btn"
        app:layout_constraintTop_toTopOf="@id/seek_bar"
        tools:text="00:00"/>

    <SeekBar
        android:id="@+id/seek_bar"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_marginEnd="8dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toStartOf="@id/duration_text"
        app:layout_constraintStart_toEndOf="@id/position_text"
        app:layout_constraintTop_toTopOf="@id/play_pause_btn"/>

    <TextView
        android:id="@+id/duration_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="8dp"
        app:layout_constraintBottom_toBottomOf="@id/seek_bar"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="@id/seek_bar"
        tools:text="00:00"/>

</androidx.constraintlayout.widget.ConstraintLayout>
```
---
## Implementing Navigation
Create a new **navigation** resource directory under **res**. In this directory create a new xml file and name it **nav_graph.xml**. Replace the xml code with the following:
```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/home_dest">

    <fragment
        android:id="@+id/home_dest"
        android:name="com.riis.mediamanager.HomeFragment">

        <action
            android:id="@+id/moveToDetailView"
            app:destination="@id/media_dest" />
    </fragment>

    <fragment
        android:id="@+id/media_dest"
        android:name="com.riis.mediamanager.MediaFragment">

        <action
            android:id="@+id/moveToHomeView"
            app:destination="@id/home_dest" />
    </fragment>

</navigation>
```
This resource is used by the `navController` in **MainActivity** for identifying and navigating to **HomeFragment** and **MediaFragment**.

---
## Implementing Other Resources
### 1. drawables
*   Go to this [Google Fonts page](https://fonts.google.com/icons?icon.query=media) and download the **Perm Media** icon resources to the  **res/drawables** directory in Android Studio. In the **res/drawables** directory you should be able to see drawables wih the prefix "baseline_perm_media_black".

*   Let's create the layout resource for the progress bar in the **HomeFragment**. Create a new xml file and name it **progress.xml**. Replace the xml code with the following:
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
        <item android:id="@android:id/background">
            <shape>
                <solid
                    android:color="@color/material_on_primary_disabled" />
            </shape>
        </item>

        <item
            android:id="@android:id/progress">
            <clip>
                <shape>
                    <solid
                        android:color="@color/teal_700" />
                </shape>
            </clip>
        </item>

    </layer-list>
    ```

### 2. Colors
*   In the **res/values** directory, open up the **colors.xml** file and replace the xml code with the following:
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <resources>
        <color name="purple_200">#FFBB86FC</color>
        <color name="purple_500">#FF6200EE</color>
        <color name="purple_700">#FF3700B3</color>
        <color name="teal_200">#FF03DAC5</color>
        <color name="teal_700">#FF018786</color>
        <color name="black">#FF000000</color>
        <color name="white">#FFFFFFFF</color>
        <color name="purplePink">#d49cff</color>
        <color name="color_accent">#FF4081</color>
    </resources>
    ```

### 3. Colors
*   In the **res/values** directory, open up the **strings.xml** file and replace the xml code with the following:
    ```xml
    <resources>
        <string name="app_name">MediaManager</string>
        <string name="percentage">%1$d %%</string>
        <string name="download_all">Download All</string>
        <string name="delete_all">Delete All</string>
        <string name="no_media_prompt">There is currently no media available on the drone\'s SD card</string>
        <string name="download_media">Download Media</string>
        <string name="view_media">View Media</string>
        <string name="delete_media">Delete Media</string>
        <string name="play">Play</string>
        <string name="pause">Pause</string>
        <string name="stop">Stop</string>
    </resources>
    ```

### 4. Themes
*   In the **res/values** directory, open up the **themes.xml** file (not the night version) and replace the xml code with the following:
    ```xml
    <resources xmlns:tools="http://schemas.android.com/tools">
        <!-- Base application theme. -->
        <style name="Theme.MediaManager" parent="Theme.MaterialComponents.Light.NoActionBar.Bridge">
            <!-- Primary brand color. -->
            <item name="colorPrimary">@color/purple_500</item>
            <item name="colorPrimaryVariant">@color/purple_700</item>
            <item name="colorOnPrimary">@color/white</item>
            <!-- Secondary brand color. -->
            <item name="colorSecondary">@color/teal_200</item>
            <item name="colorSecondaryVariant">@color/teal_700</item>
            <item name="colorOnSecondary">@color/black</item>
            <!-- Status bar color. -->
            <item name="android:statusBarColor" tools:targetApi="l">?attr/colorPrimaryVariant</item>
            <!-- Customize your theme here. -->
            <item name="windowActionBar">false</item>
        </style>
    </resources>
    ```

---
## Demo
 ![My Image](Parrot Media Manager GIF Demo.gif)





