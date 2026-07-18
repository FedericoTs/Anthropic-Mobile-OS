package org.agentnativeos.app.ui

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.agentnativeos.app.AppLaunchLog
import org.agentnativeos.app.R

/** The classic app grid — the familiar safety net behind the agent. */
class AppGridActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_grid)

        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val grid = findViewById<GridView>(R.id.grid_apps)
        grid.adapter = AppsAdapter(apps)
        grid.setOnItemClickListener { _, _, position, _ ->
            val pkg = apps[position].activityInfo.packageName
            pm.getLaunchIntentForPackage(pkg)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                AppLaunchLog(this).record(pkg) // feeds the predictive "Apps, right now" row
            }
        }
    }

    private inner class AppsAdapter(private val apps: List<ResolveInfo>) : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            val info = apps[position]
            view.findViewById<ImageView>(R.id.app_icon).setImageDrawable(info.loadIcon(packageManager))
            view.findViewById<TextView>(R.id.app_label).text = info.loadLabel(packageManager)
            return view
        }
    }
}
