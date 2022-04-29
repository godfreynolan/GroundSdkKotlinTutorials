package com.riis.mediamanager

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.parrot.drone.groundsdk.Ref
import com.parrot.drone.groundsdk.device.DeviceState
import com.parrot.drone.groundsdk.device.peripheral.MediaStore
import com.parrot.drone.groundsdk.device.peripheral.StreamServer
import com.parrot.drone.groundsdk.device.peripheral.media.MediaItem

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

