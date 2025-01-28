package com.lkubicki.foilrecord

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class RunListAdapter(
    private val files: List<File>,
    private val onItemClick: (File) -> Unit
) : RecyclerView.Adapter<RunListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.runTitleText)
        val dateText: TextView = view.findViewById(R.id.runDateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_run, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = files[position]
        val filename = file.name.substring(9, 19) // Extract datetime part

        // Format the date nicely
        val formattedDate = try {
            val pattern = "yyMMddHHmm"
            val date = SimpleDateFormat(pattern, Locale.getDefault()).parse(filename)
            SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            filename
        }

        holder.titleText.text = "Run #${files.size - position}"
        holder.dateText.text = formattedDate
        holder.itemView.setOnClickListener { onItemClick(file) }
    }

    override fun getItemCount() = files.size
}