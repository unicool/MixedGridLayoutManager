package com.example.mixedgridlayoutmanager

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = findViewById<RecyclerView>(R.id.list)
        gggo(list)
    }

    private val spanCount = 6
    private val viewTypeOfFullSpan = spanCount
    private val viewTypeOfStaggered = 0
    private val viewTypeOfGrid = spanCount / 3
    private fun gggo(list: RecyclerView) {
        list.layoutManager = MixedGridLayoutManager(
            spanCount, RecyclerView.VERTICAL
        ).apply {
            this.setSpanSizeLookup(object : MixedGridLayoutManager.SpanSizeLookup {
                override fun isStaggeredStyle(position: Int): Boolean {
                    return x(position) == viewTypeOfStaggered
                }

                override fun getStaggeredSpanSize(): Int {
                    return spanCount / 2
                }

                override fun getGridSpanSize(position: Int): Int {
                    return x(position)
                }
            })
        }

//        list.layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)

        list.adapter = object : RecyclerView.Adapter<ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val density = parent.context.resources.displayMetrics.density.toInt()
                val i10 = density * 10
                return object : ViewHolder(FrameLayout(parent.context).apply {
                    layoutParams =
                        (list.layoutManager as MixedGridLayoutManager).generateDefaultLayoutParams()
                            .apply {
                                marginStart = i10
                                marginEnd = i10
                                topMargin = i10
                                bottomMargin = i10
                            }
                }.apply {
                    addView(TextView(parent.context).apply {
                        id = android.R.id.text1
                    })
                }) {}
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                holder.itemView.findViewById<TextView>(android.R.id.text1).apply {
                    text = "${position}\t\r\n${getItemViewType(position)}\r\n${holder.itemView}"

                    val density = context.resources.displayMetrics.density.toInt()
                    val i10 = density * 10
                    val iR = density * Random.nextInt(255)
                    setPaddingRelative(i10, i10, i10, iR)
                    setBackgroundColor(Random.nextLong(0xffFFFF00).toInt())
                }
                (holder.itemView as FrameLayout).apply {}
            }

            override fun getItemViewType(position: Int): Int {
                return x(position)
            }

            override fun getItemCount(): Int {
                return 52
            }

            override fun getItemId(position: Int): Long {
                return super.getItemId(position)
            }
        }
        list.itemAnimator = DefaultItemAnimator()
    }

    private fun x(
        position: Int
    ) = when (position) {
        0 -> viewTypeOfStaggered
        1 -> viewTypeOfStaggered
        2 -> viewTypeOfStaggered
        3 -> viewTypeOfStaggered
        4 -> viewTypeOfStaggered
        5 -> viewTypeOfStaggered
        6 -> viewTypeOfStaggered
        7 -> viewTypeOfStaggered
        8 -> viewTypeOfStaggered

        9 -> viewTypeOfFullSpan

        10 -> viewTypeOfGrid
        11 -> viewTypeOfGrid
        12 -> viewTypeOfGrid
        13 -> viewTypeOfGrid
        14 -> viewTypeOfGrid
        15 -> viewTypeOfGrid
        16 -> viewTypeOfGrid
        17 -> viewTypeOfGrid
        18 -> viewTypeOfGrid
        19 -> viewTypeOfGrid
        20 -> viewTypeOfGrid


        21 -> viewTypeOfStaggered
        22 -> viewTypeOfGrid
        23 -> viewTypeOfStaggered
        24 -> viewTypeOfStaggered
        25 -> viewTypeOfGrid
        26 -> viewTypeOfGrid
        27 -> viewTypeOfStaggered
        28 -> viewTypeOfStaggered
        29 -> viewTypeOfStaggered
        30 -> viewTypeOfGrid
        31 -> viewTypeOfGrid
        32 -> viewTypeOfGrid

        33 -> viewTypeOfStaggered

        34 -> viewTypeOfFullSpan
        35 -> viewTypeOfGrid
        36 -> viewTypeOfFullSpan
        37 -> viewTypeOfGrid
        38 -> viewTypeOfGrid
        39 -> viewTypeOfFullSpan

        else -> viewTypeOfGrid
    }
}