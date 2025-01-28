package com.lkubicki.foilrecord

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class RunListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_list)

        // Set up toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val recyclerView = findViewById<RecyclerView>(R.id.runsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        loadRuns()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadRuns() {
        lifecycleScope.launch(Dispatchers.IO) {
            val files = filesDir.listFiles { file ->
                file.name.startsWith("gps_data_") && file.name.endsWith(".csv")
            }?.sortedByDescending { it.name }

            withContext(Dispatchers.Main) {
                val adapter = RunListAdapter(files?.toList() ?: emptyList()) { file ->
                    val intent = Intent(this@RunListActivity, MapViewActivity::class.java)
                    intent.putExtra("FILE_PATH", file.absolutePath)
                    startActivity(intent)
                }
                findViewById<RecyclerView>(R.id.runsRecyclerView).adapter = adapter
            }
        }
    }
}