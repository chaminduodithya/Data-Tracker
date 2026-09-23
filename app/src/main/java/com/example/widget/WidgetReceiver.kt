package com.example.widget

import android.content.Context

class WidgetReceiver : DataTrackerWidgetReceiver() {

    companion object {
        fun sendRefreshBroadcast(context: Context) {
            DataWidgetWorker.enqueueImmediate(context)
        }
    }
}
