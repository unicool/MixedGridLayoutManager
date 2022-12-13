package com.example.mixedgridlayoutmanager

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.recyclerview.widget.RecyclerView

class MixedGridLayoutManager1 : RecyclerView.LayoutManager() {
    inner class LayoutParams1 : RecyclerView.LayoutParams {
        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: MarginLayoutParams?) : super(source)
        constructor(source: ViewGroup.LayoutParams?) : super(source)
        constructor(source: RecyclerView.LayoutParams?) : super(source)
    }

    private val mOrientation = 0
    val HORIZONTAL = RecyclerView.HORIZONTAL
    val VERTICAL = RecyclerView.VERTICAL

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return if (mOrientation == HORIZONTAL) {
            LayoutParams1(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            LayoutParams1(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun canScrollHorizontally(): Boolean {
        return mOrientation == HORIZONTAL
    }

    override fun canScrollVertically(): Boolean {
        return mOrientation == VERTICAL
    }

    override fun scrollHorizontallyBy(
        dx: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?
    ): Int {
        return scrollBy(dx, recycler, state)
    }

    override fun scrollVerticallyBy(
        dy: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?
    ): Int {
        return scrollBy(dy, recycler, state)
    }

    private fun scrollBy(
        dt: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?
    ): Int {

        return -1
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {

    }
}
