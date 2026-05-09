package com.example.cradet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cradet.databinding.ActivityNearbyHospitalsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class NearbyHospitalsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityNearbyHospitalsBinding
    private lateinit var locationHelper: LocationHelper
    private var googleMap: GoogleMap? = null

    private val hospitalList = listOf(
        Hospital("City Emergency Hospital", "0.8 km", LatLng(12.9716, 77.5946)),
        Hospital("St. Luke Medical Center", "1.5 km", LatLng(12.9616, 77.5846)),
        Hospital("General Trauma Care", "2.2 km", LatLng(12.9816, 77.6046)),
        Hospital("Metro Health Clinic", "3.1 km", LatLng(12.9516, 77.5746)),
        Hospital("Red Cross Urgent Care", "4.5 km", LatLng(12.9916, 77.6146))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyHospitalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationHelper = LocationHelper(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.hospitalMap) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        setupHospitalsList()
        
        binding.btnOpenMaps.setOnClickListener {
            val gmmIntentUri = Uri.parse("geo:0,0?q=hospitals")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        hospitalList.forEach { hospital ->
            googleMap?.addMarker(MarkerOptions().position(hospital.latLng).title(hospital.name))
        }
        
        // Default zoom to first hospital area
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(hospitalList[0].latLng, 12f))
        
        googleMap?.setOnMarkerClickListener { marker ->
            val hospital = hospitalList.find { it.name == marker.title }
            hospital?.let {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${it.latLng.latitude},${it.latLng.longitude}"))
                intent.setPackage("com.google.android.apps.maps")
                startActivity(intent)
            }
            true
        }
    }

    private fun setupHospitalsList() {
        binding.rvHospitals.layoutManager = LinearLayoutManager(this)
        binding.rvHospitals.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val h = hospitalList[position]
                val tv1 = holder.itemView.findViewById<TextView>(android.R.id.text1)
                val tv2 = holder.itemView.findViewById<TextView>(android.R.id.text2)
                tv1.text = h.name
                tv1.setTextColor(android.graphics.Color.BLACK)
                tv1.setTypeface(null, android.graphics.Typeface.BOLD)
                tv2.text = h.distance
                tv2.setTextColor(android.graphics.Color.GRAY)
                
                holder.itemView.setOnClickListener {
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(h.latLng, 15f))
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${h.latLng.latitude},${h.latLng.longitude}"))
                    intent.setPackage("com.google.android.apps.maps")
                    startActivity(intent)
                }
            }
            override fun getItemCount(): Int = hospitalList.size
        }
    }

    data class Hospital(val name: String, val distance: String, val latLng: LatLng)
}
