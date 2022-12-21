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

    private val viewTypeOfFullSpan = 6
    private val viewTypeOfStaggered = 0
    private val viewTypeOfGrid = 1
    private fun gggo(list: RecyclerView) {
        list.layoutManager = MixedGridLayoutManager2(
            6, RecyclerView.VERTICAL
        ).apply {
            this.setSpanSizeLookup(object : MixedGridLayoutManager2.SpanSizeLookup{
                override fun isStaggeredStyle(position: Int): Boolean {
                    return x(position) == viewTypeOfStaggered
                }

                override fun getStaggeredSpanSize(): Int {
                    return 2
                }

                override fun getGridSpanSize(position: Int): Int {
                    return x(position)
                }
            })
        }
        list.adapter = object : RecyclerView.Adapter<ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val density = parent.context.resources.displayMetrics.density.toInt()
                val i10 = density * 10
                return object : ViewHolder(FrameLayout(parent.context).apply {
                    layoutParams =
                        (list.layoutManager as MixedGridLayoutManager2).generateDefaultLayoutParams()
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
                    text = "${position}\r\n${holder.itemView.layoutParams}"

                    val density = context.resources.displayMetrics.density.toInt()
                    val i10 = density * 10
                    val iR = density * Random.nextInt(127)
                    setPaddingRelative(i10, i10, i10, iR)
//                    println("TAGMXIED:\t${position}\t${iR}")

                    if (holder.itemViewType == viewTypeOfFullSpan) {
                        (holder.itemView.layoutParams as MixedGridLayoutManager2.LayoutParams).apply {
//                            spanSize = 2
                        }
                        setBackgroundColor(0xFFFF0000.toInt())
                    } else {
                        setBackgroundColor(Random.nextLong(0xFFFFFFFF).toInt())
                    }
                }
                (holder.itemView as FrameLayout).apply {

                }
            }

            override fun getItemViewType(position: Int): Int {
                return x(position)
            }

            override fun getItemCount(): Int {
                return 50
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
        0 -> {
            viewTypeOfStaggered
        }
        1 -> {
            viewTypeOfFullSpan
        }
        2, 3 -> {
            viewTypeOfStaggered
        }
        4 -> {
            viewTypeOfFullSpan
        }
        5, 6, 7 -> {
            viewTypeOfStaggered
        }
        8 -> {
            viewTypeOfFullSpan
        }

        in 9..20 -> {
            viewTypeOfStaggered
        }
        21 -> {
            viewTypeOfFullSpan
        }
        else -> {
            viewTypeOfGrid
        }
    }
}