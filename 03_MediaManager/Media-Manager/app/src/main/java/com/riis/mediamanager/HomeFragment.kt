package com.riis.mediamanager

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parrot.drone.groundsdk.Ref
import com.parrot.drone.groundsdk.device.DeviceState
import com.parrot.drone.groundsdk.device.peripheral.media.*
import java.io.File
import java.util.concurrent.TimeUnit

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
        val resources = mutableListOf<MediaItem.Resource>()
        for (mediaItem in sharedViewModel.mediaItemList.value?.mediaList?.get()!!){
            mediaItem.let { resources.addAll(it.resources) }
        }
        downloadMedia(resources)
    }

    private fun wipeMedia(){
        mediaStoreWiperRef?.let { sharedViewModel.mediaStoreRef?.get()?.wipe(it) }
    }

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

    override fun onDestroyView() {
        mediaDeleterRef = null
        mediaDestinationRef = null
        mediaDownloaderRef = null
        mediaStoreWiperRef = null

        super.onDestroyView()
    }
}