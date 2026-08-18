package com.tct.bot

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import org.json.JSONArray

class AndroidFFmpegBridge : mobile.FFmpegBridge {

    override fun executeFFmpeg(argsJSON: String?): String {
        if (argsJSON == null) return "Error: Arguments are null"
        
        return try {
            val jsonArray = JSONArray(argsJSON)
            val argsList = Array(jsonArray.length()) { i -> jsonArray.getString(i) }
            
            val session = FFmpegKit.executeWithArguments(argsList)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                session.allLogsAsString ?: "Success"
            } else {
                "Error: ${session.failStackTrace ?: session.allLogsAsString}"
            }
        } catch (e: Exception) {
            "Error executing FFmpeg: ${e.message}"
        }
    }

    override fun executeFFprobe(argsJSON: String?): String {
        if (argsJSON == null) return "Error: Arguments are null"
        
        return try {
            val jsonArray = JSONArray(argsJSON)
            val argsList = Array(jsonArray.length()) { i -> jsonArray.getString(i) }
            
            val session = FFprobeKit.executeWithArguments(argsList)
            
            if (ReturnCode.isSuccess(session.returnCode)) {
                session.allLogsAsString ?: "Success"
            } else {
                "Error: ${session.failStackTrace ?: session.allLogsAsString}"
            }
        } catch (e: Exception) {
            "Error executing FFprobe: ${e.message}"
        }
    }
}
