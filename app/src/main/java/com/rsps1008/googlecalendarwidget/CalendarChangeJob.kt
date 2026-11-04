package com.rsps1008.googlecalendarwidget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract

class CalendarChangeJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try { CalendarWidgetProvider.updateAll(applicationContext) }
            finally {
                jobFinished(params, false)
                schedule(applicationContext, force = true)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 71301
        fun schedule(context: Context, force: Boolean = false) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!force && scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarChangeJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(1000)
                .setTriggerContentMaxDelay(10000)
                .build()
            scheduler.schedule(job)
        }
        fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}
