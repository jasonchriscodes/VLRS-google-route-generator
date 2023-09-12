package com.spark.mapapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.libraries.places.api.model.Place
import spark.mapapp.R

class DestinationAdapter(
    private val destinations: List<Place>,
    private val onDeleteClickListener: (position: Int) -> Unit
) : RecyclerView.Adapter<DestinationAdapter.DestinationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DestinationViewHolder {
        // Inflate the item layout for the RecyclerView
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_destination, parent, false)
        return DestinationViewHolder(view)
    }

    override fun onBindViewHolder(holder: DestinationViewHolder, position: Int) {
        val destination = destinations[position]
        // Bind data to the view
        holder.bind(destination)
    }

    override fun getItemCount(): Int {
        return destinations.size
    }

    inner class DestinationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Reference views from the item layout and set data

        private val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)

        fun bind(place: Place) {
            // Example: Display destination name
            itemView.findViewById<TextView>(R.id.destinationNameTextView).text = place.name


            // Set click listener for the delete button
            deleteButton.setOnClickListener {
                // Call the onDeleteClickListener with the clicked item's position
                onDeleteClickListener.invoke(adapterPosition)
            }
        }


    }
}
