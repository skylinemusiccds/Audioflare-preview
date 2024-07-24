package com.universe.audioflare.adapter.lyrics


import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.universe.audioflare.data.model.metadata.Line
import com.universe.audioflare.data.model.metadata.Lyrics
import com.universe.audioflare.databinding.ItemLyricsActiveBinding
import com.universe.audioflare.databinding.ItemLyricsNormalBinding
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class LyricsAdapter(private var originalLyrics: Lyrics?, var translated: Lyrics? = null) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var index = -1
    private var activeLyrics: Line? = null

    interface OnItemClickListener {
        fun onItemClick(line: Line?)
    }

    lateinit var mListener: OnItemClickListener
    fun setOnItemClickListener(listener: OnItemClickListener) {
        mListener = listener
    }

    fun updateOriginalLyrics(lyrics: Lyrics) {
        if (lyrics != originalLyrics) {
            originalLyrics = lyrics
            translated = null
            notifyDataSetChanged()
        }
    }

    fun updateTranslatedLyrics(lyrics: Lyrics?) {
        if (lyrics != null) {
            translated = lyrics
            notifyDataSetChanged()
        }
    }

    fun setActiveLyrics(index: Int) {
        this.index = index
        if (index == -1) {
            if (activeLyrics != null) {
                activeLyrics = null
                notifyDataSetChanged()
            }
        } else {
            if (originalLyrics?.lines?.get(index) != activeLyrics) {
                activeLyrics = originalLyrics?.lines?.get(index)
                notifyItemChanged(index)
                if (index > 0) {
                    notifyItemChanged(index - 1)
                }
            }
        }
    }

    inner class ActiveViewHolder(
        val binding: ItemLyricsActiveBinding,
        val listener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: Line) {
            binding.root.setOnClickListener {
                listener.onItemClick(line)
                showFullScreenLyrics(line)  // Show full-screen lyrics on click
            }
            binding.tvNowLyrics.text = line.words + "\n" // Add spacing
            binding.tvNowLyrics.setTextColor(Color.WHITE) // White for active

            if (translated != null && translated?.lines?.find { it.startTimeMs == line.startTimeMs }?.words != null) {
                translateText(line.words, "en", object : TranslationCallback {
                    override fun onTranslationCompleted(translation: String) {
                        binding.tvTranslatedLyrics.visibility = View.VISIBLE
                        binding.tvTranslatedLyrics.text = translation + "\n" // Add spacing
                        binding.tvTranslatedLyrics.setTextColor(Color.WHITE) // White for active translation
                    }

                    override fun onTranslationFailed() {
                        binding.tvTranslatedLyrics.visibility = View.GONE
                    }
                })
            } else {
                binding.tvTranslatedLyrics.visibility = View.GONE
            }

            // Increase font size for active line (adjust the value as needed)
            binding.tvNowLyrics.textSize = binding.tvNowLyrics.textSize + 1
        }
    }

    inner class NormalViewHolder(
        val binding: ItemLyricsNormalBinding,
        val listener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: Line) {
            binding.root.setOnClickListener {
                listener.onItemClick(line)
                showFullScreenLyrics(line)  // Show full-screen lyrics on click
            }
            binding.tvLyrics.text = line.words + "\n" // Add spacing
            binding.tvLyrics.setTextColor(Color.parseColor("#968989")) // Custom color for upcoming lyrics

            if (translated != null && translated?.lines?.find { it.startTimeMs == line.startTimeMs }?.words != null) {
                translateText(line.words, "en", object : TranslationCallback {
                    override fun onTranslationCompleted(translation: String) {
                        binding.tvTranslatedLyrics.visibility = View.VISIBLE
                        binding.tvTranslatedLyrics.text = translation + "\n" // Add spacing
                        binding.tvTranslatedLyrics.setTextColor(Color.WHITE) // White for upcoming translation
                    }

                    override fun onTranslationFailed() {
                        binding.tvTranslatedLyrics.visibility = View.GONE
                    }
                })
            } else {
                binding.tvTranslatedLyrics.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ACTIVE -> ActiveViewHolder(
                ItemLyricsActiveBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ), mListener
            )

            else -> NormalViewHolder(
                ItemLyricsNormalBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ), mListener
            )
        }
    }

    override fun getItemCount(): Int {
        return originalLyrics?.lines?.size ?: 0
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ActiveViewHolder -> {
                holder.bind(originalLyrics?.lines?.get(position) ?: return)
            }

            is NormalViewHolder -> {
                holder.bind(originalLyrics?.lines?.get(position) ?: return)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (originalLyrics?.lines?.get(position) == activeLyrics) {
            TYPE_ACTIVE
        } else {
            TYPE_NORMAL
        }
    }

    companion object {
        const val TYPE_ACTIVE = 0
        const val TYPE_NORMAL = 1
        const val RAPID_API_KEY = "a620530aacmshfb000fcd3f4cf5ep126753jsn7afb3f5346aa" // Replace with your RapidAPI key

        // Function to translate text using RapidAPI
        private fun translateText(text: String, targetLanguage: String, callback: TranslationCallback) {
            val client = OkHttpClient()
            val url = "https://google-translate113.p.rapidapi.com/api/v1/translator/html"
            val mediaType = MediaType.parse("application/x-www-form-urlencoded")
            val body = RequestBody.create(mediaType, "q=$text&target=$targetLanguage&source=auto")
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("content-type", "application/x-www-form-urlencoded")
                .addHeader("accept-encoding", "application/gzip")
                .addHeader("x-rapidapi-key", RAPID_API_KEY)
                .addHeader("x-rapidapi-host", "google-translate113.p.rapidapi.com")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback.onTranslationFailed()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            callback.onTranslationFailed()
                            return
                        }

                        try {
                            val jsonResponse = JSONObject(response.body()?.string())
                            val translation = jsonResponse.getString("translatedText")
                            callback.onTranslationCompleted(translation)
                        } catch (e: JSONException) {
                            callback.onTranslationFailed()
                        }
                    }
                }
            })
        }
    }

    interface TranslationCallback {
        fun onTranslationCompleted(translation: String)
        fun onTranslationFailed()
    }

    // Function to show full-screen lyrics
    private fun showFullScreenLyrics(line: Line) {
        val context = itemView.context
        val builder = AlertDialog.Builder(context)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_fullscreen_lyrics, null)
        val tvFullLyrics = view.findViewById<TextView>(R.id.tvFullLyrics)
        tvFullLyrics.text = line.words

        if (translated != null) {
            translateText(line.words, "en", object : TranslationCallback {
                override fun onTranslationCompleted(translation: String) {
                    tvFullLyrics.text = "${line.words}\n\n$translation"
                }

                override fun onTranslationFailed() {
                    tvFullLyrics.text = line.words
                }
            })
        }

        builder.setView(view)
        val dialog = builder.create()
        dialog.show()
    }
}



/* 

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.universe.audioflare.data.model.metadata.Line
import com.universe.audioflare.data.model.metadata.Lyrics
import com.universe.audioflare.databinding.ItemLyricsActiveBinding
import com.universe.audioflare.databinding.ItemLyricsNormalBinding

class LyricsAdapter(private var originalLyrics: Lyrics?, var translated: Lyrics? = null) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var index = -1
    private var activeLyrics: Line? = null

    interface OnItemClickListener {
        fun onItemClick(line: Line?)
    }

    lateinit var mListener: OnItemClickListener
    fun setOnItemClickListener(listener: OnItemClickListener) {
        mListener = listener
    }

    fun updateOriginalLyrics(lyrics: Lyrics) {
        if (lyrics != originalLyrics) {
            originalLyrics = lyrics
            translated = null
            notifyDataSetChanged()
        }
    }

    fun updateTranslatedLyrics(lyrics: Lyrics?) {
        if (lyrics != null) {
            translated = lyrics
            notifyDataSetChanged()
        }
    }

    fun setActiveLyrics(index: Int) {
        this.index = index
        if (index == -1) {
            if (activeLyrics != null) {
                activeLyrics = null
                notifyDataSetChanged()
            }
        } else {
            if (originalLyrics?.lines?.get(index) != activeLyrics) {
                activeLyrics = originalLyrics?.lines?.get(index)
                notifyItemChanged(index)
                if (index > 0) {
                    notifyItemChanged(index - 1)
                }
            }
        }
    }

    inner class ActiveViewHolder(
        val binding: ItemLyricsActiveBinding,
        val listener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: Line) {
            binding.root.setOnClickListener {
                listener.onItemClick(line)
            }
            binding.tvNowLyrics.text = line.words + "\n" // Add spacing
            binding.tvNowLyrics.setTextColor(Color.WHITE) // White for active

            if (translated != null && translated?.lines?.find { it.startTimeMs == line.startTimeMs }?.words != null) {
                translated?.lines?.find { it.startTimeMs == line.startTimeMs }?.words?.let {
                    binding.tvTranslatedLyrics.visibility = View.VISIBLE
                    binding.tvTranslatedLyrics.text = it + "\n" // Add spacing
                    binding.tvTranslatedLyrics.setTextColor(Color.WHITE) // White for active translation
                }
            } else {
                binding.tvTranslatedLyrics.visibility = View.GONE
            }

            // Increase font size for active line (adjust the value as needed)
            binding.tvNowLyrics.textSize = binding.tvNowLyrics.textSize + 1
        }
    }

    inner class NormalViewHolder(
        val binding: ItemLyricsNormalBinding,
        val listener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(line: Line) {
            binding.root.setOnClickListener {
                listener.onItemClick(line)
            }
            binding.tvLyrics.text = line.words + "\n" // Add spacing
            binding.tvLyrics.setTextColor(Color.BLACK) // Black for upcoming

            if (translated != null && translated?.lines?.find { it.startTimeMs == line.startTimeMs }?.words != null) {
                translated?.lines?.find { it.startTimeMs == line.startTimeMs }?.words?.let {
                    binding.tvTranslatedLyrics.visibility = View.VISIBLE
                    binding.tvTranslatedLyrics.text = it + "\n" // Add spacing
                    binding.tvTranslatedLyrics.setTextColor(Color.BLACK) // Black for upcoming translation
                }
            } else {
                binding.tvTranslatedLyrics.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ACTIVE -> ActiveViewHolder(
                ItemLyricsActiveBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ), mListener
            )

            else -> NormalViewHolder(
                ItemLyricsNormalBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ), mListener
            )
        }
    }

    override fun getItemCount(): Int {
        return originalLyrics?.lines?.size ?: 0
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ActiveViewHolder -> {
                holder.bind(originalLyrics?.lines?.get(position) ?: return)
            }

            is NormalViewHolder -> {
                holder.bind(originalLyrics?.lines?.get(position) ?: return)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (originalLyrics?.lines?.get(position) == activeLyrics) {
            TYPE_ACTIVE
        } else {
            TYPE_NORMAL
        }
    }

    companion object {
        const val TYPE_ACTIVE = 0
        const val TYPE_NORMAL = 1
    }
}*/