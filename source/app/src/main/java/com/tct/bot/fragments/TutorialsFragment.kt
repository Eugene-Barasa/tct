package com.tct.bot.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tct.bot.MainActivity
import com.tct.bot.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class TutorialsFragment : Fragment(R.layout.fragment_tutorials) {

    private lateinit var rvTutorials: RecyclerView
    private lateinit var progressBar: View
    private val CACHE_PREFS = "TutorialsCache"
    private val CACHE_TIME_KEY = "last_fetch_time"
    private val CACHE_JSON_KEY = "cached_json"

    data class VideoItem(val id: String, val title: String, val url: String, val description: String)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val btnRefresh = view.findViewById<ImageView>(R.id.btn_refresh)
        btnRefresh.setOnClickListener {
            val prefs = requireContext().getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            Toast.makeText(context, "Refreshing tutorials...", Toast.LENGTH_SHORT).show()
            fetchFromServer(prefs)
        }

        rvTutorials = view.findViewById(R.id.rv_tutorials)
        progressBar = view.findViewById(R.id.progress_bar)
        
        rvTutorials.layoutManager = LinearLayoutManager(requireContext())

        loadTutorials()
    }

    private fun loadTutorials() {
        val prefs = requireContext().getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(CACHE_TIME_KEY, 0L)
        val cachedJson = prefs.getString(CACHE_JSON_KEY, "")
        
        val currentTime = System.currentTimeMillis()
        val threeHours = 3 * 60 * 60 * 1000L

        if (cachedJson!!.isNotEmpty() && (currentTime - lastFetch) < threeHours) {
            parseAndDisplayJson(cachedJson)
        } else {
            fetchFromServer(prefs)
        }
    }

    private fun fetchFromServer(prefs: android.content.SharedPreferences) {
        progressBar.visibility = View.VISIBLE
        rvTutorials.visibility = View.GONE

        thread {
            try {
                val url = URL("https://gist.githubusercontent.com/i-tct/2268e37a7f874b3bb8d6a9b9393edeaa/raw/Videos.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    prefs.edit()
                        .putLong(CACHE_TIME_KEY, System.currentTimeMillis())
                        .putString(CACHE_JSON_KEY, jsonResponse)
                        .apply()

                    activity?.runOnUiThread { parseAndDisplayJson(jsonResponse) }
                } else {
                    activity?.runOnUiThread { 
                        Toast.makeText(context, "Failed to load tutorials. Code: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "Error fetching tutorials: ${e.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    
                    val fallbackJson = prefs.getString(CACHE_JSON_KEY, "")
                    if (!fallbackJson.isNullOrEmpty()) {
                        parseAndDisplayJson(fallbackJson)
                    }
                }
            }
        }
    }

    private fun parseAndDisplayJson(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val jsonArray = jsonObject.getJSONArray("videos")
            val videoList = mutableListOf<VideoItem>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                videoList.add(
                    VideoItem(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        url = item.getString("url"),
                        description = item.getString("description")
                    )
                )
            }

            rvTutorials.adapter = TutorialAdapter(videoList) { video ->
                val embedUrl = convertToEmbedUrl(video.url)
                (activity as MainActivity).navigateTo(WebViewFragment.newInstance(embedUrl, video.title))
            }
            
            progressBar.visibility = View.GONE
            rvTutorials.visibility = View.VISIBLE

        } catch (e: Exception) {
            Toast.makeText(context, "Error parsing tutorials.", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
        }
    }

    inner class TutorialAdapter(
        private val videos: List<VideoItem>,
        private val onClick: (VideoItem) -> Unit
    ) : RecyclerView.Adapter<TutorialAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_title)
            val tvDescription: TextView = view.findViewById(R.id.tv_description)
            val ivThumbnail: ImageView = view.findViewById(R.id.iv_thumbnail)
            
            init {
                view.setOnClickListener { onClick(videos[adapterPosition]) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tutorial, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val video = videos[position]
            holder.tvTitle.text = video.title
            holder.tvDescription.text = video.description
            
            val videoId = extractVideoId(video.url)
            loadThumbnailImage(holder.ivThumbnail, videoId)
        }

        override fun getItemCount() = videos.size
        
        private fun loadThumbnailImage(imageView: ImageView, videoId: String) {
            if (videoId.isEmpty()) return
            val thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            
            Glide.with(imageView.context)
                .load(thumbnailUrl)
                .centerCrop()
                .into(imageView)
        }
    }

    private fun extractVideoId(url: String): String {
        return when {
            url.contains("v=") -> url.split("v=")[1].split("&")[0]
            url.contains("youtu.be/") -> url.split("youtu.be/")[1].split("?")[0]
            else -> ""
        }
    }

    private fun convertToEmbedUrl(url: String): String {
        val videoId = extractVideoId(url)
        if (videoId.isEmpty()) return url
        
        return "https://www.youtube.com/embed/$videoId?rel=0&modestbranding=1&playsinline=1&autoplay=1"
    }
}
