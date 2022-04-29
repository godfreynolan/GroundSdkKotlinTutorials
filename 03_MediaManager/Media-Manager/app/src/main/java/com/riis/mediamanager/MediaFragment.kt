package com.riis.mediamanager

import android.os.Bundle
import android.os.Handler
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.parrot.drone.groundsdk.Ref
import com.parrot.drone.groundsdk.device.peripheral.media.MediaItem
import com.parrot.drone.groundsdk.device.peripheral.stream.MediaReplay
import com.parrot.drone.groundsdk.stream.GsdkStreamView
import com.parrot.drone.groundsdk.stream.Replay
import com.parrot.drone.groundsdk.stream.Stream
import java.util.concurrent.TimeUnit

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