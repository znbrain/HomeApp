package io.github.domi04151309.home.adapters

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import io.github.domi04151309.home.R
import android.view.LayoutInflater
import android.widget.Switch
import io.github.domi04151309.home.data.ListViewItem
import android.widget.CompoundButton
import io.github.domi04151309.home.interfaces.RecyclerViewHelperInterface
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation

class MainListAdapter(private val helperInterface: RecyclerViewHelperInterface) : RecyclerView.Adapter<MainListAdapter.ViewHolder>() {

    private var items: ArrayList<ListViewItem> = arrayListOf()
    private var stateListener: CompoundButton.OnCheckedChangeListener? = null
    private var animate: Boolean = true

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        return ViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.list_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.drawable.setImageResource(items[position].icon)
        holder.title.text = items[position].title
        holder.summary.text = items[position].summary
        holder.hidden.text = items[position].hidden
        if (stateListener != null) {
            holder.stateSwitch.isChecked = items[position].state ?: false
            holder.stateSwitch.setOnCheckedChangeListener(stateListener)
        } else {
            holder.stateSwitch.visibility = View.GONE
        }
        holder.itemView.setOnClickListener { helperInterface.onItemClicked(holder.itemView, position) }
        if (animate) playAnimation(holder.itemView)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.stateSwitch.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int {
        return items.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: ArrayList<ListViewItem>, newStateListener: CompoundButton.OnCheckedChangeListener? = null) {
        if (newItems.size != items.size || newStateListener != stateListener) {
            animate = true
            items = newItems
            stateListener = newStateListener
            notifyDataSetChanged()
        } else {
            animate = false
            val changed = arrayListOf<Int>()
            for (i in 0 until items.size) {
                if (items[i] != newItems[i]) changed.add(i)
            }
            items = newItems
            changed.forEach(::notifyItemChanged)
        }
    }

    fun updateSwitch(position: Int, state: Boolean) {
        if (items[position].state != state) {
            animate = false
            items[position].state = state
            notifyItemChanged(position)
        }
    }

    private fun playAnimation(v: View) {
        val set = AnimationSet(true)

        val firstAnimation: Animation = AlphaAnimation(0.0f, 1.0f)
        firstAnimation.duration = 300
        set.addAnimation(firstAnimation)

        val secondAnimation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, -1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f
        )
        secondAnimation.duration = 300
        set.addAnimation(secondAnimation)

        v.startAnimation(set)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val drawable: ImageView = view.findViewById(R.id.drawable)
        val title: TextView = view.findViewById(R.id.title)
        val summary: TextView = view.findViewById(R.id.summary)
        val hidden: TextView = view.findViewById(R.id.hidden)
        val stateSwitch: Switch = view.findViewById<Switch>(R.id.state)
    }
}