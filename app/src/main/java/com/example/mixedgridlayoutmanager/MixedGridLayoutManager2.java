package com.example.mixedgridlayoutmanager;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.Supplier;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * A LayoutManager that lays out children in a staggered grid formation.
 * It supports horizontal & vertical layout as well as an ability to layout children in reverse.
 * <p>
 * Staggered grids are likely to have gaps at the edges of the layout. To avoid these gaps,
 * StaggeredGridLayoutManager can offset spans independently or move items between spans. You can
 * control this behavior via {@link #setGapStrategy(int)}.
 */
public class MixedGridLayoutManager2 extends RecyclerView.LayoutManager implements
        RecyclerView.SmoothScroller.ScrollVectorProvider {

    private static final String TAG = "FINNN";

    static final boolean DEBUG = true;

    public static final int HORIZONTAL = RecyclerView.HORIZONTAL;

    public static final int VERTICAL = RecyclerView.VERTICAL;

    /**
     * Does not do anything to hide gaps.
     */
    public static final int GAP_HANDLING_NONE = 0;

    /**
     * When scroll state is changed to {@link RecyclerView#SCROLL_STATE_IDLE}, StaggeredGrid will
     * check if there are gaps in the because of full span items. If it finds, it will re-layout
     * and move items to correct positions with animations.
     * <p>
     * For example, if LayoutManager ends up with the following layout due to adapter changes:
     * <pre>
     * AAA
     * _BC
     * DDD
     * </pre>
     * <p>
     * It will animate to the following state:
     * <pre>
     * AAA
     * BC_
     * DDD
     * </pre>
     */
    public static final int GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS = 2;

    static final int INVALID_OFFSET = Integer.MIN_VALUE;
    /**
     * While trying to find next view to focus, LayoutManager will not try to scroll more
     * than this factor times the total space of the list. If layout is vertical, total space is the
     * height minus padding, if layout is horizontal, total space is the width minus padding.
     */
    private static final float MAX_SCROLL_FACTOR = 1 / 3f;

    /**
     * Number of spans
     */
    private int mSpanCount = -1;

    Span[] mSpans;

    SpanSizeLookup mSpanSizeLookup = new SpanSizeLookup() {
        @Override
        public boolean isStaggeredStyle(int position) {
            return true;
        }

        @Override
        public int getStaggeredSpanSize() {
            return 1;
        }

        @Override
        public int getGridSpanSize(int position) {
            return 1;
        }
    };

    /**
     * Primary orientation is the layout's orientation, secondary orientation is the orientation
     * for spans. Having both makes code much cleaner for calculations.
     */
    @NonNull
    OrientationHelper mPrimaryOrientation;
    @NonNull
    OrientationHelper mSecondaryOrientation;

    private int mOrientation;

    /**
     * The width or height per span, depending on the orientation.
     */
    private int mSizePerSpan;

    @NonNull
    private final LayoutState mLayoutState;

    boolean mReverseLayout = false;

    /**
     * Aggregated reverse layout value that takes RTL into account.
     */
    boolean mShouldReverseLayout = false;

    /**
     * Temporary variable used during fill method to check which spans needs to be filled.
     */
    private BitSet mRemainingSpans;

    /**
     * When LayoutManager needs to scroll to a position, it sets this variable and requests a
     * layout which will check this variable and re-layout accordingly.
     */
    int mPendingScrollPosition = RecyclerView.NO_POSITION;

    /**
     * Used to keep the offset value when {@link #scrollToPositionWithOffset(int, int)} is
     * called.
     */
    int mPendingScrollPositionOffset = INVALID_OFFSET;

    /**
     * Keeps the mapping between the adapter positions and spans. This is necessary to provide
     * a consistent experience when user scrolls the list.
     */
    LazySpanLookup mLazySpanLookup = new LazySpanLookup();

    /**
     * how we handle gaps in UI.
     */
    private int mGapStrategy = GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS;

    /**
     * Saved state needs this information to properly layout on restore.
     */
    private boolean mLastLayoutFromEnd;

    /**
     * Saved state and onLayout needs this information to re-layout properly
     */
    private boolean mLastLayoutRTL;

    /**
     * SavedState is not handled until a layout happens. This is where we keep it until next
     * layout.
     */
    private SavedState mPendingSavedState;

    /**
     * Re-used measurement specs. updated by onLayout.
     */
    private int mFullSizeSpec;

    /**
     * Re-used rectangle to get child decor offsets.
     */
    private final Rect mTmpRect = new Rect();

    /**
     * Re-used anchor info.
     */
    private final AnchorInfo mAnchorInfo = new AnchorInfo();

    /**
     * If a full span item is invalid / or created in reverse direction; it may create gaps in
     * the UI. While laying out, if such case is detected, we set this flag.
     * <p>
     * After scrolling stops, we check this flag and if it is set, re-layout.
     */
    private boolean mLaidOutInvalidAlignSpan = false;

    /**
     * Works the same way as {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}.
     * see {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}
     */
    private final boolean mSmoothScrollbarEnabled = true;

    /**
     * Temporary array used (solely in {@link #collectAdjacentPrefetchPositions}) for stashing and
     * sorting distances to views being prefetched.
     */
    private int[] mPrefetchDistances;

    ///private final Runnable mCheckForGapsRunnable = this::checkForGaps;

    /**
     * Constructor used when layout manager is set in XML by RecyclerView attribute
     * "layoutManager". Defaults to single column and vertical.
     */
    @SuppressWarnings("unused")
    public MixedGridLayoutManager2(Context context, AttributeSet attrs, int defStyleAttr,
                                   int defStyleRes) {
        Properties properties = getProperties(context, attrs, defStyleAttr, defStyleRes);
        setOrientation(properties.orientation);
        setSpanCount(properties.spanCount);
        setReverseLayout(properties.reverseLayout);
        mLayoutState = new LayoutState();
        createOrientationHelpers();
    }

    /**
     * Creates a StaggeredGridLayoutManager with given parameters.
     *
     * @param spanCount   If orientation is vertical, spanCount is number of columns. If
     *                    orientation is horizontal, spanCount is number of rows.
     * @param orientation {@link #VERTICAL} or {@link #HORIZONTAL}
     */
    public MixedGridLayoutManager2(int spanCount, int orientation) {
        mOrientation = orientation;
        setSpanCount(spanCount);
        mLayoutState = new LayoutState();
        createOrientationHelpers();
    }

    @Override
    public boolean isAutoMeasureEnabled() {
        return mGapStrategy != GAP_HANDLING_NONE;
    }

    private void createOrientationHelpers() {
        mPrimaryOrientation = OrientationHelper.createOrientationHelper(this, mOrientation);
        mSecondaryOrientation = OrientationHelper
                .createOrientationHelper(this, 1 - mOrientation);
    }

    /**
     * Checks for gaps in the UI that may be caused by adapter changes.
     * <p>
     * When a full span item is laid out in reverse direction, it sets a flag which we check when
     * scroll is stopped (or re-layout happens) and re-layout after first valid item.
     */
    boolean checkForGaps() {
        if (getChildCount() == 0 || mGapStrategy == GAP_HANDLING_NONE || !isAttachedToWindow()) {
            return false;
        }
        final int minPos, maxPos;
        if (mShouldReverseLayout) {
            minPos = getLastChildPosition();
            maxPos = getFirstChildPosition();
        } else {
            minPos = getFirstChildPosition();
            maxPos = getLastChildPosition();
        }
        if (minPos == 0) {
            View gapView = hasGapsToFix();
            if (gapView != null) {
                mLazySpanLookup.clear();
                requestSimpleAnimationsInNextLayout();
                requestLayout();
                return true;
            }
        }
        if (!mLaidOutInvalidAlignSpan) {
            return false;
        }
        int invalidGapDir = mShouldReverseLayout ? LayoutState.LAYOUT_START : LayoutState.LAYOUT_END;
        final LazySpanLookup.AlignSpanItem invalidFsi = mLazySpanLookup
                .getFirstFullSpanItemInRange(minPos, maxPos + 1, invalidGapDir, true);
        if (invalidFsi == null) {
            mLaidOutInvalidAlignSpan = false;
            mLazySpanLookup.forceInvalidateAfter(maxPos + 1);
            return false;
        }
        final LazySpanLookup.AlignSpanItem validFsi = mLazySpanLookup
                .getFirstFullSpanItemInRange(minPos, invalidFsi.mPosition,
                        invalidGapDir * -1, true);
        if (validFsi == null) {
            mLazySpanLookup.forceInvalidateAfter(invalidFsi.mPosition);
        } else {
            mLazySpanLookup.forceInvalidateAfter(validFsi.mPosition + 1);
        }
        requestSimpleAnimationsInNextLayout();
        requestLayout();
        return true;
    }

    @Override
    public void onScrollStateChanged(int state) {
        if (state == RecyclerView.SCROLL_STATE_IDLE) {
            checkForGaps();
        }
    }

    @Override
    public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);

        ///removeCallbacks(mCheckForGapsRunnable);
        for (int i = 0; i < mSpanCount; i++) {
            mSpans[i].clear();
        }
        // SGLM will require fresh layout call to recover state after detach
        view.requestLayout();
    }

    /**
     * Checks for gaps if we've reached to the top of the list.
     * <p>
     * Intermediate gaps created by full span items are tracked via mLaidOutInvalidFullSpan field.
     */
    View hasGapsToFix() {
        int startChildIndex = 0;
        int endChildIndex = getChildCount() - 1;
        BitSet mSpansToCheck = new BitSet(mSpanCount);
        mSpansToCheck.set(0, mSpanCount, true);

        final int firstChildIndex, childLimit;
        final int preferredSpanDir = mOrientation == VERTICAL && isLayoutRTL() ? 1 : -1;

        if (mShouldReverseLayout) {
            firstChildIndex = endChildIndex;
            childLimit = startChildIndex - 1;
        } else {
            firstChildIndex = startChildIndex;
            childLimit = endChildIndex + 1;
        }
        final int nextChildDiff = firstChildIndex < childLimit ? 1 : -1;
        for (int i = firstChildIndex; i != childLimit; i += nextChildDiff) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (mSpansToCheck.get(lp.mSpan.mIndex)) {
                if (checkSpanForGap(lp.mSpan)) {
                    return child;
                }
                mSpansToCheck.clear(lp.mSpan.mIndex);
            }
            if (lp.isAlignSpan()) {
                continue; // quick reject
            }

            if (i + nextChildDiff != childLimit) {
                View nextChild = getChildAt(i + nextChildDiff);
                boolean compareSpans = false;
                if (mShouldReverseLayout) {
                    // ensure child's end is below nextChild's end
                    int myEnd = mPrimaryOrientation.getDecoratedEnd(child);
                    int nextEnd = mPrimaryOrientation.getDecoratedEnd(nextChild);
                    if (myEnd < nextEnd) {
                        return child; //i should have a better position
                    } else if (myEnd == nextEnd) {
                        compareSpans = true;
                    }
                } else {
                    int myStart = mPrimaryOrientation.getDecoratedStart(child);
                    int nextStart = mPrimaryOrientation.getDecoratedStart(nextChild);
                    if (myStart > nextStart) {
                        return child; //i should have a better position
                    } else if (myStart == nextStart) {
                        compareSpans = true;
                    }
                }
                if (compareSpans) {
                    // equal, check span indices.
                    LayoutParams nextLp = (LayoutParams) nextChild.getLayoutParams();
                    if (lp.mSpan.mIndex - nextLp.mSpan.mIndex < 0 != preferredSpanDir < 0) {
                        return child;
                    }
                }
            }
        }
        // everything looks good
        return null;
    }

    private boolean checkSpanForGap(Span span) {
        if (mShouldReverseLayout) {
            if (span.getEndLine() < mPrimaryOrientation.getEndAfterPadding()) {
                // if it is full span, it is OK
                final View endView = span.mViews.get(span.mViews.size() - 1);
                final LayoutParams lp = span.getLayoutParams(endView);
                return lp.isStaggeredSpan();
            }
        } else if (span.getStartLine() > mPrimaryOrientation.getStartAfterPadding()) {
            // if it is full span, it is OK
            final View startView = span.mViews.get(0);
            final LayoutParams lp = span.getLayoutParams(startView);
            return lp.isStaggeredSpan();
        }
        return false;
    }

    /**
     * Sets the number of spans for the layout. This will invalidate all of the span assignments
     * for Views.
     * <p>
     * Calling this method will automatically result in a new layout request unless the spanCount
     * parameter is equal to current span count.
     *
     * @param spanCount Number of spans to layout
     */
    public void setSpanCount(int spanCount) {
        assertNotInLayoutOrScroll(null);
        if (spanCount != mSpanCount) {
            invalidateSpanAssignments();
            mSpanCount = spanCount;
            mRemainingSpans = new BitSet(mSpanCount);
            mSpans = new Span[mSpanCount];
            for (int i = 0; i < mSpanCount; i++) {
                mSpans[i] = new Span(i);
            }
            requestLayout();
        }
    }

    public void setSpanSizeLookup(SpanSizeLookup spanSizeLookup) {
        this.mSpanSizeLookup = spanSizeLookup;
    }

    public SpanSizeLookup getSpanSizeLookup() {
        return mSpanSizeLookup;
    }

    /**
     * Sets the orientation of the layout. StaggeredGridLayoutManager will do its best to keep
     * scroll position if this method is called after views are laid out.
     *
     * @param orientation {@link #HORIZONTAL} or {@link #VERTICAL}
     */
    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException("invalid orientation.");
        }
        assertNotInLayoutOrScroll(null);
        if (orientation == mOrientation) {
            return;
        }
        mOrientation = orientation;
        OrientationHelper tmp = mPrimaryOrientation;
        mPrimaryOrientation = mSecondaryOrientation;
        mSecondaryOrientation = tmp;
        requestLayout();
    }

    /**
     * Sets whether LayoutManager should start laying out items from the end of the UI. The order
     * items are traversed is not affected by this call.
     * <p>
     * For vertical layout, if it is set to <code>true</code>, first item will be at the bottom of
     * the list.
     * <p>
     * For horizontal layouts, it depends on the layout direction.
     * When set to true, If {@link RecyclerView} is LTR, than it will layout from RTL, if
     * {@link RecyclerView}} is RTL, it will layout from LTR.
     *
     * @param reverseLayout Whether layout should be in reverse or not
     */
    public void setReverseLayout(boolean reverseLayout) {
        assertNotInLayoutOrScroll(null);
        if (mPendingSavedState != null && mPendingSavedState.mReverseLayout != reverseLayout) {
            mPendingSavedState.mReverseLayout = reverseLayout;
        }
        mReverseLayout = reverseLayout;
        requestLayout();
    }

    /**
     * Returns the current gap handling strategy for StaggeredGridLayoutManager.
     * <p>
     * Staggered grid may have gaps in the layout due to changes in the adapter. To avoid gaps,
     * StaggeredGridLayoutManager provides 2 options. Check {@link #GAP_HANDLING_NONE} and
     * {@link #GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS} for details.
     * <p>
     * By default, StaggeredGridLayoutManager uses {@link #GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS}.
     *
     * @return Current gap handling strategy.
     * @see #setGapStrategy(int)
     * @see #GAP_HANDLING_NONE
     * @see #GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
     */
    public int getGapStrategy() {
        return mGapStrategy;
    }

    /**
     * Sets the gap handling strategy for StaggeredGridLayoutManager. If the gapStrategy parameter
     * is different than the current strategy, calling this method will trigger a layout request.
     *
     * @param gapStrategy The new gap handling strategy. Should be
     *                    {@link #GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS} or {@link
     *                    #GAP_HANDLING_NONE}.
     * @see #getGapStrategy()
     */
    public void setGapStrategy(int gapStrategy) {
        assertNotInLayoutOrScroll(null);
        if (gapStrategy == mGapStrategy) {
            return;
        }
        if (gapStrategy != GAP_HANDLING_NONE
                && gapStrategy != GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS) {
            throw new IllegalArgumentException("invalid gap strategy. Must be GAP_HANDLING_NONE "
                    + "or GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS");
        }
        mGapStrategy = gapStrategy;
        requestLayout();
    }

    @Override
    public void assertNotInLayoutOrScroll(String message) {
        if (mPendingSavedState == null) {
            super.assertNotInLayoutOrScroll(message);
        }
    }

    /**
     * Returns the number of spans laid out by StaggeredGridLayoutManager.
     *
     * @return Number of spans in the layout
     */
    public int getSpanCount() {
        return mSpanCount;
    }

    /**
     * For consistency, StaggeredGridLayoutManager keeps a mapping between spans and items.
     * <p>
     * If you need to cancel current assignments, you can call this method which will clear all
     * assignments and request a new layout.
     */
    public void invalidateSpanAssignments() {
        mLazySpanLookup.clear();
        requestLayout();
    }

    /**
     * Calculates the views' layout order. (e.g. from end to start or start to end)
     * RTL layout support is applied automatically. So if layout is RTL and
     * {@link #getReverseLayout()} is {@code true}, elements will be laid out starting from left.
     */
    private void resolveShouldLayoutReverse() {
        // A == B is the same result, but we rather keep it readable
        if (mOrientation == VERTICAL || !isLayoutRTL()) {
            mShouldReverseLayout = mReverseLayout;
        } else {
            mShouldReverseLayout = !mReverseLayout;
        }
    }

    boolean isLayoutRTL() {
        return getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    /**
     * Returns whether views are laid out in reverse order or not.
     * <p>
     * Not that this value is not affected by RecyclerView's layout direction.
     *
     * @return True if layout is reversed, false otherwise
     * @see #setReverseLayout(boolean)
     */
    public boolean getReverseLayout() {
        return mReverseLayout;
    }

    @Override
    public void setMeasuredDimension(Rect childrenBounds, int wSpec, int hSpec) {
        // we don't like it to wrap content in our non-scroll direction.
        final int width, height;
        final int horizontalPadding = getPaddingLeft() + getPaddingRight();
        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        if (mOrientation == VERTICAL) {
            final int usedHeight = childrenBounds.height() + verticalPadding;
            height = chooseSize(hSpec, usedHeight, getMinimumHeight());
            width = chooseSize(wSpec, mSizePerSpan * mSpanCount + horizontalPadding,
                    getMinimumWidth());
        } else {
            final int usedWidth = childrenBounds.width() + horizontalPadding;
            width = chooseSize(wSpec, usedWidth, getMinimumWidth());
            height = chooseSize(hSpec, mSizePerSpan * mSpanCount + verticalPadding,
                    getMinimumHeight());
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        onLayoutChildren(recycler, state, true);
    }


    private void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state, boolean shouldCheckForGaps) {
        final AnchorInfo anchorInfo = mAnchorInfo;
        if (mPendingSavedState != null || mPendingScrollPosition != RecyclerView.NO_POSITION) {
            if (state.getItemCount() == 0) {
                removeAndRecycleAllViews(recycler);
                anchorInfo.reset();
                return;
            }
        }

        boolean recalculateAnchor = !anchorInfo.mValid || mPendingScrollPosition != RecyclerView.NO_POSITION
                || mPendingSavedState != null;
        if (recalculateAnchor) {
            anchorInfo.reset();
            if (mPendingSavedState != null) {
                applyPendingSavedState(anchorInfo);
            } else {
                resolveShouldLayoutReverse();
                anchorInfo.mLayoutFromEnd = mShouldReverseLayout;
            }
            updateAnchorInfoForLayout(state, anchorInfo);
            anchorInfo.mValid = true;
        }
        if (mPendingSavedState == null && mPendingScrollPosition == RecyclerView.NO_POSITION) {
            if (anchorInfo.mLayoutFromEnd != mLastLayoutFromEnd
                    || isLayoutRTL() != mLastLayoutRTL) {
                mLazySpanLookup.clear();
                anchorInfo.mInvalidateOffsets = true;
            }
        }

        if (getChildCount() > 0 && (mPendingSavedState == null
                || mPendingSavedState.mSpanOffsetsSize < 1)) {
            if (anchorInfo.mInvalidateOffsets) {
                for (int i = 0; i < mSpanCount; i++) {
                    // Scroll to position is set, clear.
                    mSpans[i].clear();
                    if (anchorInfo.mOffset != INVALID_OFFSET) {
                        mSpans[i].setLine(anchorInfo.mOffset);
                    }
                }
            } else {
                if (recalculateAnchor || mAnchorInfo.mSpanReferenceLines == null) {
                    for (int i = 0; i < mSpanCount; i++) {
                        mSpans[i].cacheReferenceLineAndClear(mShouldReverseLayout,
                                anchorInfo.mOffset);
                    }
                    mAnchorInfo.saveSpanReferenceLines(mSpans);
                } else {
                    for (int i = 0; i < mSpanCount; i++) {
                        final Span span = mSpans[i];
                        span.clear();
                        span.setLine(mAnchorInfo.mSpanReferenceLines[i]);
                    }
                }
            }
        }
        detachAndScrapAttachedViews(recycler);
        mLayoutState.mRecycle = false;
        mLaidOutInvalidAlignSpan = false;
        updateMeasureSpecs(mSecondaryOrientation.getTotalSpace());
        updateLayoutState(anchorInfo.mPosition, state);
        if (anchorInfo.mLayoutFromEnd) {
            // Layout start.
            setLayoutStateDirection(LayoutState.LAYOUT_START);
            fill(recycler, mLayoutState, state);
            // Layout end.
            setLayoutStateDirection(LayoutState.LAYOUT_END);
            mLayoutState.mCurrentPosition = anchorInfo.mPosition + mLayoutState.mItemDirection;
            fill(recycler, mLayoutState, state);
        } else {
            // Layout end.
            setLayoutStateDirection(LayoutState.LAYOUT_END);
            fill(recycler, mLayoutState, state);
            // Layout start.
            setLayoutStateDirection(LayoutState.LAYOUT_START);
            mLayoutState.mCurrentPosition = anchorInfo.mPosition + mLayoutState.mItemDirection;
            fill(recycler, mLayoutState, state);
        }

        repositionToWrapContentIfNecessary();

        if (getChildCount() > 0) {
            if (mShouldReverseLayout) {
                fixEndGap(recycler, state, true);
                fixStartGap(recycler, state, false);
            } else {
                fixStartGap(recycler, state, true);
                fixEndGap(recycler, state, false);
            }
        }
        boolean hasGaps = false;
        if (shouldCheckForGaps && !state.isPreLayout()) {
            final boolean needToCheckForGaps = mGapStrategy != GAP_HANDLING_NONE
                    && getChildCount() > 0
                    && (mLaidOutInvalidAlignSpan || hasGapsToFix() != null);
            if (needToCheckForGaps) {
                ///removeCallbacks(mCheckForGapsRunnable);
                if (checkForGaps()) {
                    hasGaps = true;
                }
            }
        }
        if (state.isPreLayout()) {
            mAnchorInfo.reset();
        }
        mLastLayoutFromEnd = anchorInfo.mLayoutFromEnd;
        mLastLayoutRTL = isLayoutRTL();
        if (hasGaps) {
            mAnchorInfo.reset();
            onLayoutChildren(recycler, state, false);
        }
    }

    @Override
    public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);
        mPendingScrollPosition = RecyclerView.NO_POSITION;
        mPendingScrollPositionOffset = INVALID_OFFSET;
        mPendingSavedState = null; // we don't need this anymore
        mAnchorInfo.reset();
    }

    private void repositionToWrapContentIfNecessary() {
        if (mSecondaryOrientation.getMode() == View.MeasureSpec.EXACTLY) {
            return; // nothing to do
        }
        float maxSize = 0;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            float size = mSecondaryOrientation.getDecoratedMeasurement(child);
            if (size < maxSize) {
                continue;
            }
            LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
            if (layoutParams.isAlignSpan()) {
                size = size / layoutParams.getSpanSize();
            }
            maxSize = Math.max(maxSize, size);
        }
        int before = mSizePerSpan;
        int desired = Math.round(maxSize * mSpanCount);
        if (mSecondaryOrientation.getMode() == View.MeasureSpec.AT_MOST) {
            desired = Math.min(desired, mSecondaryOrientation.getTotalSpace());
        }
        updateMeasureSpecs(desired);
        if (mSizePerSpan == before) {
            return; // nothing has changed
        }
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (lp.isFullSpan()) {
                continue;
            }
            if (isLayoutRTL() && mOrientation == VERTICAL) {
                int newOffset = -(mSpanCount - 1 - lp.mSpan.mIndex) * mSizePerSpan;
                int prevOffset = -(mSpanCount - 1 - lp.mSpan.mIndex) * before;
                child.offsetLeftAndRight(newOffset - prevOffset);
            } else {
                int newOffset = lp.mSpan.mIndex * mSizePerSpan;
                int prevOffset = lp.mSpan.mIndex * before;
                if (mOrientation == VERTICAL) {
                    child.offsetLeftAndRight(newOffset - prevOffset);
                } else {
                    child.offsetTopAndBottom(newOffset - prevOffset);
                }
            }
        }
    }

    private void applyPendingSavedState(AnchorInfo anchorInfo) {
        if (DEBUG) {
            Log.d(TAG, "found saved state: " + mPendingSavedState);
        }
        if (mPendingSavedState.mSpanOffsetsSize > 0) {
            if (mPendingSavedState.mSpanOffsetsSize == mSpanCount) {
                for (int i = 0; i < mSpanCount; i++) {
                    mSpans[i].clear();
                    int line = mPendingSavedState.mSpanOffsets[i];
                    if (line != Span.INVALID_LINE) {
                        if (mPendingSavedState.mAnchorLayoutFromEnd) {
                            line += mPrimaryOrientation.getEndAfterPadding();
                        } else {
                            line += mPrimaryOrientation.getStartAfterPadding();
                        }
                    }
                    mSpans[i].setLine(line);
                }
            } else {
                mPendingSavedState.invalidateSpanInfo();
                mPendingSavedState.mAnchorPosition = mPendingSavedState.mVisibleAnchorPosition;
            }
        }
        mLastLayoutRTL = mPendingSavedState.mLastLayoutRTL;
        setReverseLayout(mPendingSavedState.mReverseLayout);
        resolveShouldLayoutReverse();

        if (mPendingSavedState.mAnchorPosition != RecyclerView.NO_POSITION) {
            mPendingScrollPosition = mPendingSavedState.mAnchorPosition;
            anchorInfo.mLayoutFromEnd = mPendingSavedState.mAnchorLayoutFromEnd;
        } else {
            anchorInfo.mLayoutFromEnd = mShouldReverseLayout;
        }
        if (mPendingSavedState.mSpanLookupSize > 1) {
            mLazySpanLookup.mData = mPendingSavedState.mSpanLookup;
            mLazySpanLookup.mAlignSpanItems = mPendingSavedState.mAlignSpanItems;
        }
    }

    void updateAnchorInfoForLayout(RecyclerView.State state, AnchorInfo anchorInfo) {
        if (updateAnchorFromPendingData(state, anchorInfo)) {
            return;
        }
        if (updateAnchorFromChildren(state, anchorInfo)) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Deciding anchor info from fresh state");
        }
        anchorInfo.assignCoordinateFromPadding();
        anchorInfo.mPosition = 0;
    }

    private boolean updateAnchorFromChildren(RecyclerView.State state, AnchorInfo anchorInfo) {
        // We don't recycle views out of adapter order. This way, we can rely on the first or
        // last child as the anchor position.
        // Layout direction may change but we should select the child depending on the latest
        // layout direction. Otherwise, we'll choose the wrong child.
        anchorInfo.mPosition = mLastLayoutFromEnd
                ? findLastReferenceChildPosition(state.getItemCount())
                : findFirstReferenceChildPosition(state.getItemCount());
        anchorInfo.mOffset = INVALID_OFFSET;
        return true;
    }

    boolean updateAnchorFromPendingData(RecyclerView.State state, AnchorInfo anchorInfo) {
        // Validate scroll position if exists.
        if (state.isPreLayout() || mPendingScrollPosition == RecyclerView.NO_POSITION) {
            return false;
        }
        // Validate it.
        if (mPendingScrollPosition < 0 || mPendingScrollPosition >= state.getItemCount()) {
            mPendingScrollPosition = RecyclerView.NO_POSITION;
            mPendingScrollPositionOffset = INVALID_OFFSET;
            return false;
        }

        if (mPendingSavedState == null || mPendingSavedState.mAnchorPosition == RecyclerView.NO_POSITION
                || mPendingSavedState.mSpanOffsetsSize < 1) {
            // If item is visible, make it fully visible.
            final View child = findViewByPosition(mPendingScrollPosition);
            if (child != null) {
                // Use regular anchor position, offset according to pending offset and target
                // child
                anchorInfo.mPosition = mShouldReverseLayout ? getLastChildPosition()
                        : getFirstChildPosition();
                if (mPendingScrollPositionOffset != INVALID_OFFSET) {
                    if (anchorInfo.mLayoutFromEnd) {
                        final int target = mPrimaryOrientation.getEndAfterPadding()
                                - mPendingScrollPositionOffset;
                        anchorInfo.mOffset = target - mPrimaryOrientation.getDecoratedEnd(child);
                    } else {
                        final int target = mPrimaryOrientation.getStartAfterPadding()
                                + mPendingScrollPositionOffset;
                        anchorInfo.mOffset = target - mPrimaryOrientation.getDecoratedStart(child);
                    }
                    return true;
                }

                // no offset provided. Decide according to the child location
                final int childSize = mPrimaryOrientation.getDecoratedMeasurement(child);
                if (childSize > mPrimaryOrientation.getTotalSpace()) {
                    // Item does not fit. Fix depending on layout direction.
                    anchorInfo.mOffset = anchorInfo.mLayoutFromEnd
                            ? mPrimaryOrientation.getEndAfterPadding()
                            : mPrimaryOrientation.getStartAfterPadding();
                    return true;
                }

                final int startGap = mPrimaryOrientation.getDecoratedStart(child)
                        - mPrimaryOrientation.getStartAfterPadding();
                if (startGap < 0) {
                    anchorInfo.mOffset = -startGap;
                    return true;
                }
                final int endGap = mPrimaryOrientation.getEndAfterPadding()
                        - mPrimaryOrientation.getDecoratedEnd(child);
                if (endGap < 0) {
                    anchorInfo.mOffset = endGap;
                    return true;
                }
                // child already visible. just layout as usual
                anchorInfo.mOffset = INVALID_OFFSET;
            } else {
                // Child is not visible. Set anchor coordinate depending on in which direction
                // child will be visible.
                anchorInfo.mPosition = mPendingScrollPosition;
                if (mPendingScrollPositionOffset == INVALID_OFFSET) {
                    final int position = calculateScrollDirectionForPosition(
                            anchorInfo.mPosition);
                    anchorInfo.mLayoutFromEnd = position == LayoutState.LAYOUT_END;
                    anchorInfo.assignCoordinateFromPadding();
                } else {
                    anchorInfo.assignCoordinateFromPadding(mPendingScrollPositionOffset);
                }
                anchorInfo.mInvalidateOffsets = true;
            }
        } else {
            anchorInfo.mOffset = INVALID_OFFSET;
            anchorInfo.mPosition = mPendingScrollPosition;
        }
        return true;
    }

    void updateMeasureSpecs(int totalSpace) {
        mSizePerSpan = totalSpace / mSpanCount;
        //noinspection ResourceType
        mFullSizeSpec = View.MeasureSpec.makeMeasureSpec(
                totalSpace, mSecondaryOrientation.getMode());
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return mPendingSavedState == null;
    }

    /**
     * Returns the adapter position of the first visible view for each span.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * StaggeredGridLayoutManager may pre-cache some views that are not necessarily visible. Those
     * views are ignored in this method.
     *
     * @param into An array to put the results into. If you don't provide any, LayoutManager will
     *             create a new one.
     * @return The adapter position of the first visible item in each span. If a span does not have
     * any items, {@link RecyclerView#NO_POSITION} is returned for that span.
     * @see #findFirstCompletelyVisibleItemPositions(int[])
     * @see #findLastVisibleItemPositions(int[])
     */
    public int[] findFirstVisibleItemPositions(int[] into) {
        if (into == null) {
            into = new int[mSpanCount];
        } else if (into.length < mSpanCount) {
            throw new IllegalArgumentException("Provided int[]'s size must be more than or equal"
                    + " to span count. Expected:" + mSpanCount + ", array size:" + into.length);
        }
        for (int i = 0; i < mSpanCount; i++) {
            into[i] = mSpans[i].findFirstVisibleItemPosition();
        }
        return into;
    }

    /**
     * Returns the adapter position of the first completely visible view for each span.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * StaggeredGridLayoutManager may pre-cache some views that are not necessarily visible. Those
     * views are ignored in this method.
     *
     * @param into An array to put the results into. If you don't provide any, LayoutManager will
     *             create a new one.
     * @return The adapter position of the first fully visible item in each span. If a span does
     * not have any items, {@link RecyclerView#NO_POSITION} is returned for that span.
     * @see #findFirstVisibleItemPositions(int[])
     * @see #findLastCompletelyVisibleItemPositions(int[])
     */
    public int[] findFirstCompletelyVisibleItemPositions(int[] into) {
        if (into == null) {
            into = new int[mSpanCount];
        } else if (into.length < mSpanCount) {
            throw new IllegalArgumentException("Provided int[]'s size must be more than or equal"
                    + " to span count. Expected:" + mSpanCount + ", array size:" + into.length);
        }
        for (int i = 0; i < mSpanCount; i++) {
            into[i] = mSpans[i].findFirstCompletelyVisibleItemPosition();
        }
        return into;
    }

    /**
     * Returns the adapter position of the last visible view for each span.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * StaggeredGridLayoutManager may pre-cache some views that are not necessarily visible. Those
     * views are ignored in this method.
     *
     * @param into An array to put the results into. If you don't provide any, LayoutManager will
     *             create a new one.
     * @return The adapter position of the last visible item in each span. If a span does not have
     * any items, {@link RecyclerView#NO_POSITION} is returned for that span.
     * @see #findLastCompletelyVisibleItemPositions(int[])
     * @see #findFirstVisibleItemPositions(int[])
     */
    public int[] findLastVisibleItemPositions(int[] into) {
        if (into == null) {
            into = new int[mSpanCount];
        } else if (into.length < mSpanCount) {
            throw new IllegalArgumentException("Provided int[]'s size must be more than or equal"
                    + " to span count. Expected:" + mSpanCount + ", array size:" + into.length);
        }
        for (int i = 0; i < mSpanCount; i++) {
            into[i] = mSpans[i].findLastVisibleItemPosition();
        }
        return into;
    }

    /**
     * Returns the adapter position of the last completely visible view for each span.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * StaggeredGridLayoutManager may pre-cache some views that are not necessarily visible. Those
     * views are ignored in this method.
     *
     * @param into An array to put the results into. If you don't provide any, LayoutManager will
     *             create a new one.
     * @return The adapter position of the last fully visible item in each span. If a span does not
     * have any items, {@link RecyclerView#NO_POSITION} is returned for that span.
     * @see #findFirstCompletelyVisibleItemPositions(int[])
     * @see #findLastVisibleItemPositions(int[])
     */
    public int[] findLastCompletelyVisibleItemPositions(int[] into) {
        if (into == null) {
            into = new int[mSpanCount];
        } else if (into.length < mSpanCount) {
            throw new IllegalArgumentException("Provided int[]'s size must be more than or equal"
                    + " to span count. Expected:" + mSpanCount + ", array size:" + into.length);
        }
        for (int i = 0; i < mSpanCount; i++) {
            into[i] = mSpans[i].findLastCompletelyVisibleItemPosition();
        }
        return into;
    }

    @Override
    public int computeHorizontalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset(state);
    }

    private int computeScrollOffset(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        return ScrollbarHelper.computeScrollOffset(state, mPrimaryOrientation,
                findFirstVisibleItemClosestToStart(!mSmoothScrollbarEnabled),
                findFirstVisibleItemClosestToEnd(!mSmoothScrollbarEnabled),
                this, mSmoothScrollbarEnabled, mShouldReverseLayout);
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset(state);
    }

    @Override
    public int computeHorizontalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent(state);
    }

    private int computeScrollExtent(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        return ScrollbarHelper.computeScrollExtent(state, mPrimaryOrientation,
                findFirstVisibleItemClosestToStart(!mSmoothScrollbarEnabled),
                findFirstVisibleItemClosestToEnd(!mSmoothScrollbarEnabled),
                this, mSmoothScrollbarEnabled);
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent(state);
    }

    @Override
    public int computeHorizontalScrollRange(RecyclerView.State state) {
        return computeScrollRange(state);
    }

    private int computeScrollRange(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        return ScrollbarHelper.computeScrollRange(state, mPrimaryOrientation,
                findFirstVisibleItemClosestToStart(!mSmoothScrollbarEnabled),
                findFirstVisibleItemClosestToEnd(!mSmoothScrollbarEnabled),
                this, mSmoothScrollbarEnabled);
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return computeScrollRange(state);
    }

    private void measureChildWithDecorationsAndMargin(View child, LayoutParams lp, boolean alreadyMeasured) {
        if (lp.isFullSpan()) {
            if (mOrientation == VERTICAL) {
                measureChildWithDecorationsAndMargin(child,
                        mFullSizeSpec,
                        getChildMeasureSpec(getHeight(), getHeightMode(),
                                getPaddingTop() + getPaddingBottom(), lp.height, true),
                        alreadyMeasured);
            } else {
                measureChildWithDecorationsAndMargin(child,
                        getChildMeasureSpec(getWidth(), getWidthMode(),
                                getPaddingLeft() + getPaddingRight(), lp.width, true),
                        mFullSizeSpec,
                        alreadyMeasured);
            }
        } else {
            final int spanSize = lp.getSpanSize();
            final int mSizePerSpan = this.mSizePerSpan * spanSize; // 跨格
            if (mOrientation == VERTICAL) {
                // Padding for width measure spec is 0 because left and right padding were already
                // factored into mSizePerSpan.
                measureChildWithDecorationsAndMargin(child,
                        getChildMeasureSpec(mSizePerSpan, getWidthMode(),
                                0, lp.width, false),
                        getChildMeasureSpec(getHeight(), getHeightMode(),
                                getPaddingTop() + getPaddingBottom(), lp.height, true),
                        alreadyMeasured);
            } else {
                // Padding for height measure spec is 0 because top and bottom padding were already
                // factored into mSizePerSpan.
                measureChildWithDecorationsAndMargin(child,
                        getChildMeasureSpec(getWidth(), getWidthMode(),
                                getPaddingLeft() + getPaddingRight(), lp.width, true),
                        getChildMeasureSpec(mSizePerSpan, getHeightMode(),
                                0, lp.height, false),
                        alreadyMeasured);
            }
        }
    }

    private void measureChildWithDecorationsAndMargin(View view, LayoutParams lp, int maxSize) {
        final int totalSpaceInOther = this.mSizePerSpan * lp.getSpanSize(); // 跨格
        final int wSpec;
        final int hSpec;
        if (mOrientation == VERTICAL) {
            wSpec = getChildMeasureSpec(totalSpaceInOther, getWidthMode(), // View.MeasureSpec.EXACTLY
                    0, lp.width, false);
            hSpec = View.MeasureSpec.makeMeasureSpec(maxSize, View.MeasureSpec.EXACTLY);
        } else {
            wSpec = View.MeasureSpec.makeMeasureSpec(maxSize, View.MeasureSpec.EXACTLY);
            hSpec = getChildMeasureSpec(totalSpaceInOther, getHeightMode(), // View.MeasureSpec.EXACTLY
                    0, lp.height, false);
        }
        measureChildWithDecorationsAndMargin(view, wSpec, hSpec, true);
    }

    private void measureChildWithDecorationsAndMargin(View child, int widthSpec,
                                                      int heightSpec, boolean alreadyMeasured) {
        calculateItemDecorationsForChild(child, mTmpRect);
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        widthSpec = updateSpecWithExtra(widthSpec, lp.leftMargin + mTmpRect.left,
                lp.rightMargin + mTmpRect.right);
        heightSpec = updateSpecWithExtra(heightSpec, lp.topMargin + mTmpRect.top,
                lp.bottomMargin + mTmpRect.bottom);
        final boolean measure = alreadyMeasured
                ? shouldReMeasureChild(child, widthSpec, heightSpec, lp)
                : shouldMeasureChild(child, widthSpec, heightSpec, lp);
        if (measure) {
            child.measure(widthSpec, heightSpec);
        }
    }

    /**
     * RecyclerView internally does its own View measurement caching which should help with
     * WRAP_CONTENT.
     * <p>
     * Use this method if the View is already measured once in this layout pass.
     */
    boolean shouldReMeasureChild(View child, int widthSpec, int heightSpec, RecyclerView.LayoutParams lp) {
        return !isMeasurementCacheEnabled()
                || !isMeasurementUpToDate(child.getMeasuredWidth(), widthSpec, lp.width)
                || !isMeasurementUpToDate(child.getMeasuredHeight(), heightSpec, lp.height);
    }

    // we may consider making this public

    /**
     * RecyclerView internally does its own View measurement caching which should help with
     * WRAP_CONTENT.
     * <p>
     * Use this method if the View is not yet measured and you need to decide whether to
     * measure this View or not.
     */
    boolean shouldMeasureChild(View child, int widthSpec, int heightSpec, RecyclerView.LayoutParams lp) {
        return child.isLayoutRequested()
                || !isMeasurementCacheEnabled()
                || !isMeasurementUpToDate(child.getWidth(), widthSpec, lp.width)
                || !isMeasurementUpToDate(child.getHeight(), heightSpec, lp.height);
    }

    private static boolean isMeasurementUpToDate(int childSize, int spec, int dimension) {
        final int specMode = View.MeasureSpec.getMode(spec);
        final int specSize = View.MeasureSpec.getSize(spec);
        if (dimension > 0 && childSize != dimension) {
            return false;
        }
        switch (specMode) {
            case View.MeasureSpec.UNSPECIFIED:
                return true;
            case View.MeasureSpec.AT_MOST:
                return specSize >= childSize;
            case View.MeasureSpec.EXACTLY:
                return specSize == childSize;
        }
        return false;
    }


    private int updateSpecWithExtra(int spec, int startInset, int endInset) {
        if (startInset == 0 && endInset == 0) {
            return spec;
        }
        final int mode = View.MeasureSpec.getMode(spec);
        if (mode == View.MeasureSpec.AT_MOST || mode == View.MeasureSpec.EXACTLY) {
            return View.MeasureSpec.makeMeasureSpec(
                    Math.max(0, View.MeasureSpec.getSize(spec) - startInset - endInset), mode);
        }
        return spec;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            mPendingSavedState = (SavedState) state;
            requestLayout();
        } else if (DEBUG) {
            Log.d(TAG, "invalid saved state class");
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        if (mPendingSavedState != null) {
            return new SavedState(mPendingSavedState);
        }
        SavedState state = new SavedState();
        state.mReverseLayout = mReverseLayout;
        state.mAnchorLayoutFromEnd = mLastLayoutFromEnd;
        state.mLastLayoutRTL = mLastLayoutRTL;

        if (mLazySpanLookup != null && mLazySpanLookup.mData != null) {
            state.mSpanLookup = mLazySpanLookup.mData;
            state.mSpanLookupSize = state.mSpanLookup.length;
            state.mAlignSpanItems = mLazySpanLookup.mAlignSpanItems;
        } else {
            state.mSpanLookupSize = 0;
        }

        if (getChildCount() > 0) {
            state.mAnchorPosition = mLastLayoutFromEnd ? getLastChildPosition()
                    : getFirstChildPosition();
            state.mVisibleAnchorPosition = findFirstVisibleItemPositionInt();
            state.mSpanOffsetsSize = mSpanCount;
            state.mSpanOffsets = new int[mSpanCount];
            for (int i = 0; i < mSpanCount; i++) {
                int line;
                if (mLastLayoutFromEnd) {
                    line = mSpans[i].getEndLine(Span.INVALID_LINE);
                    if (line != Span.INVALID_LINE) {
                        line -= mPrimaryOrientation.getEndAfterPadding();
                    }
                } else {
                    line = mSpans[i].getStartLine(Span.INVALID_LINE);
                    if (line != Span.INVALID_LINE) {
                        line -= mPrimaryOrientation.getStartAfterPadding();
                    }
                }
                state.mSpanOffsets[i] = line;
            }
        } else {
            state.mAnchorPosition = RecyclerView.NO_POSITION;
            state.mVisibleAnchorPosition = RecyclerView.NO_POSITION;
            state.mSpanOffsetsSize = 0;
        }
        if (DEBUG) {
            Log.d(TAG, "saved state:\n" + state);
        }
        return state;
    }

    @Override
    public void onInitializeAccessibilityNodeInfoForItem(RecyclerView.Recycler recycler,
                                                         RecyclerView.State state, View host, AccessibilityNodeInfoCompat info) {
        ViewGroup.LayoutParams lp = host.getLayoutParams();
        if (!(lp instanceof LayoutParams)) {
            throw new RuntimeException("please use `" + LayoutParams.class + "`, instead of: " + lp + ", it's hard for me, thx.");
//            super.onInitializeAccessibilityNodeInfoForItem(host, info);
//            return;
        }
        LayoutParams sglp = (LayoutParams) lp;
        if (mOrientation == HORIZONTAL) {
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    sglp.getSpanIndex(), sglp.getSpanSize(),
                    -1, -1, false, false));
        } else { // VERTICAL
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    -1, -1,
                    sglp.getSpanIndex(), sglp.getSpanSize(), false, false));
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (getChildCount() > 0) {
            final View start = findFirstVisibleItemClosestToStart(false);
            final View end = findFirstVisibleItemClosestToEnd(false);
            if (start == null || end == null) {
                return;
            }
            final int startPos = getPosition(start);
            final int endPos = getPosition(end);
            if (startPos < endPos) {
                event.setFromIndex(startPos);
                event.setToIndex(endPos);
            } else {
                event.setFromIndex(endPos);
                event.setToIndex(startPos);
            }
        }
    }

    /**
     * Finds the first fully visible child to be used as an anchor child if span count changes when
     * state is restored. If no children is fully visible, returns a partially visible child instead
     * of returning null.
     */
    int findFirstVisibleItemPositionInt() {
        final View first = mShouldReverseLayout ? findFirstVisibleItemClosestToEnd(true) :
                findFirstVisibleItemClosestToStart(true);
        return first == null ? RecyclerView.NO_POSITION : getPosition(first);
    }

    @Override
    public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
                                           RecyclerView.State state) {
        if (mOrientation == HORIZONTAL) {
            return mSpanCount;
        }
        return super.getRowCountForAccessibility(recycler, state);
    }

    @Override
    public int getColumnCountForAccessibility(RecyclerView.Recycler recycler,
                                              RecyclerView.State state) {
        if (mOrientation == VERTICAL) {
            return mSpanCount;
        }
        return super.getColumnCountForAccessibility(recycler, state);
    }

    /**
     * This is for internal use. Not necessarily the child closest to start but the first child
     * we find that matches the criteria.
     * This method does not do any sorting based on child's start coordinate, instead, it uses
     * children order.
     */
    View findFirstVisibleItemClosestToStart(boolean fullyVisible) {
        final int boundsStart = mPrimaryOrientation.getStartAfterPadding();
        final int boundsEnd = mPrimaryOrientation.getEndAfterPadding();
        final int limit = getChildCount();
        View partiallyVisible = null;
        for (int i = 0; i < limit; i++) {
            final View child = getChildAt(i);
            final int childStart = mPrimaryOrientation.getDecoratedStart(child);
            final int childEnd = mPrimaryOrientation.getDecoratedEnd(child);
            if (childEnd <= boundsStart || childStart >= boundsEnd) {
                continue; // not visible at all
            }
            if (childStart >= boundsStart || !fullyVisible) {
                // when checking for start, it is enough even if part of the child's top is visible
                // as long as fully visible is not requested.
                return child;
            }
            if (partiallyVisible == null) {
                partiallyVisible = child;
            }
        }
        return partiallyVisible;
    }

    /**
     * This is for internal use. Not necessarily the child closest to bottom but the first child
     * we find that matches the criteria.
     * This method does not do any sorting based on child's end coordinate, instead, it uses
     * children order.
     */
    View findFirstVisibleItemClosestToEnd(boolean fullyVisible) {
        final int boundsStart = mPrimaryOrientation.getStartAfterPadding();
        final int boundsEnd = mPrimaryOrientation.getEndAfterPadding();
        View partiallyVisible = null;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            final int childStart = mPrimaryOrientation.getDecoratedStart(child);
            final int childEnd = mPrimaryOrientation.getDecoratedEnd(child);
            if (childEnd <= boundsStart || childStart >= boundsEnd) {
                continue; // not visible at all
            }
            if (childEnd <= boundsEnd || !fullyVisible) {
                // when checking for end, it is enough even if part of the child's bottom is visible
                // as long as fully visible is not requested.
                return child;
            }
            if (partiallyVisible == null) {
                partiallyVisible = child;
            }
        }
        return partiallyVisible;
    }

    private void fixEndGap(RecyclerView.Recycler recycler, RecyclerView.State state,
                           boolean canOffsetChildren) {
        final int maxEndLine = getMaxEnd(Integer.MIN_VALUE);
        if (maxEndLine == Integer.MIN_VALUE) {
            return;
        }
        int gap = mPrimaryOrientation.getEndAfterPadding() - maxEndLine;
        int fixOffset;
        if (gap > 0) {
            fixOffset = -scrollBy(-gap, recycler, state);
        } else {
            return; // nothing to fix
        }
        gap -= fixOffset;
        if (canOffsetChildren && gap > 0) {
            mPrimaryOrientation.offsetChildren(gap);
        }
    }

    private void fixStartGap(RecyclerView.Recycler recycler, RecyclerView.State state,
                             boolean canOffsetChildren) {
        final int minStartLine = getMinStart(Integer.MAX_VALUE);
        if (minStartLine == Integer.MAX_VALUE) {
            return;
        }
        int gap = minStartLine - mPrimaryOrientation.getStartAfterPadding();
        int fixOffset;
        if (gap > 0) {
            fixOffset = scrollBy(gap, recycler, state);
        } else {
            return; // nothing to fix
        }
        gap -= fixOffset;
        if (canOffsetChildren && gap > 0) {
            mPrimaryOrientation.offsetChildren(-gap);
        }
    }

    private void updateLayoutState(int anchorPosition, RecyclerView.State state) {
        mLayoutState.mAvailable = 0;
        mLayoutState.mCurrentPosition = anchorPosition;
        int startExtra = 0;
        int endExtra = 0;
        if (isSmoothScrolling()) {
            final int targetPos = state.getTargetScrollPosition();
            if (targetPos != RecyclerView.NO_POSITION) {
                if (mShouldReverseLayout == targetPos < anchorPosition) {
                    endExtra = mPrimaryOrientation.getTotalSpace();
                } else {
                    startExtra = mPrimaryOrientation.getTotalSpace();
                }
            }
        }

        // Line of the furthest row.
        final boolean clipToPadding = getClipToPadding();
        if (clipToPadding) {
            mLayoutState.mStartLine = mPrimaryOrientation.getStartAfterPadding() - startExtra;
            mLayoutState.mEndLine = mPrimaryOrientation.getEndAfterPadding() + endExtra;
        } else {
            mLayoutState.mEndLine = mPrimaryOrientation.getEnd() + endExtra;
            mLayoutState.mStartLine = -startExtra;
        }
        mLayoutState.mStopInFocusable = false;
        mLayoutState.mRecycle = true;
        mLayoutState.mInfinite = mPrimaryOrientation.getMode() == View.MeasureSpec.UNSPECIFIED
                && mPrimaryOrientation.getEnd() == 0;
    }

    private void setLayoutStateDirection(int direction) {
        mLayoutState.mLayoutDirection = direction;
        mLayoutState.mItemDirection = (mShouldReverseLayout == (direction == LayoutState.LAYOUT_START))
                ? LayoutState.ITEM_DIRECTION_TAIL : LayoutState.ITEM_DIRECTION_HEAD;
    }

    @Override
    public void offsetChildrenHorizontal(int dx) {
        super.offsetChildrenHorizontal(dx);
        for (int i = 0; i < mSpanCount; i++) {
            mSpans[i].onOffset(dx);
        }
    }

    @Override
    public void offsetChildrenVertical(int dy) {
        super.offsetChildrenVertical(dy);
        for (int i = 0; i < mSpanCount; i++) {
            mSpans[i].onOffset(dy);
        }
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        handleUpdate(positionStart, itemCount, AdapterHelper.UpdateOp.REMOVE);
    }

    @Override
    public void onItemsAdded(RecyclerView recyclerView, int positionStart, int itemCount) {
        handleUpdate(positionStart, itemCount, AdapterHelper.UpdateOp.ADD);
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        mLazySpanLookup.clear();
        requestLayout();
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int from, int to, int itemCount) {
        handleUpdate(from, to, AdapterHelper.UpdateOp.MOVE);
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount,
                               Object payload) {
        handleUpdate(positionStart, itemCount, AdapterHelper.UpdateOp.UPDATE);
    }

    /**
     * Checks whether it should invalidate span assignments in response to an adapter change.
     */
    private void handleUpdate(int positionStart, int itemCountOrToPosition, int cmd) {
        int minPosition = mShouldReverseLayout ? getLastChildPosition() : getFirstChildPosition();
        final int affectedRangeEnd; // exclusive
        final int affectedRangeStart; // inclusive

        if (cmd == AdapterHelper.UpdateOp.MOVE) {
            if (positionStart < itemCountOrToPosition) {
                affectedRangeEnd = itemCountOrToPosition + 1;
                affectedRangeStart = positionStart;
            } else {
                affectedRangeEnd = positionStart + 1;
                affectedRangeStart = itemCountOrToPosition;
            }
        } else {
            affectedRangeStart = positionStart;
            affectedRangeEnd = positionStart + itemCountOrToPosition;
        }

        mLazySpanLookup.invalidateAfter(affectedRangeStart);
        switch (cmd) {
            case AdapterHelper.UpdateOp.ADD:
                mLazySpanLookup.offsetForAddition(positionStart, itemCountOrToPosition);
                break;
            case AdapterHelper.UpdateOp.REMOVE:
                mLazySpanLookup.offsetForRemoval(positionStart, itemCountOrToPosition);
                break;
            case AdapterHelper.UpdateOp.MOVE:
                // sdk todo optimize
                mLazySpanLookup.offsetForRemoval(positionStart, 1);
                mLazySpanLookup.offsetForAddition(itemCountOrToPosition, 1);
                break;
        }

        if (affectedRangeEnd <= minPosition) {
            return;
        }

        int maxPosition = mShouldReverseLayout ? getFirstChildPosition() : getLastChildPosition();
        if (affectedRangeStart <= maxPosition) {
            requestLayout();
        }
    }

    private int fill(RecyclerView.Recycler recycler, LayoutState layoutState, RecyclerView.State state) {
        mRemainingSpans.set(0, mSpanCount, true);
        // The target position we are trying to reach.
        final int targetLine;

        // Line of the furthest row.
        if (mLayoutState.mInfinite) {
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                targetLine = Integer.MAX_VALUE;
            } else { // LAYOUT_START
                targetLine = Integer.MIN_VALUE;
            }
        } else {
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                targetLine = layoutState.mEndLine + layoutState.mAvailable;
            } else { // LAYOUT_START
                targetLine = layoutState.mStartLine - layoutState.mAvailable;
            }
        }

        updateAllRemainingSpans(null, layoutState.mLayoutDirection, targetLine);
        if (DEBUG) {
            Log.d(TAG, "FILLING targetLine: " + targetLine + ","
                    + "remaining spans:" + mRemainingSpans + ", state: " + layoutState);
        }

        // the default coordinate to add new view.
        final int defaultNewViewLine = mShouldReverseLayout
                ? mPrimaryOrientation.getEndAfterPadding()
                : mPrimaryOrientation.getStartAfterPadding();
        boolean added = false;
        Brother nextView = null;
        View preView = null;
        while (mLayoutState.mInfinite || !mRemainingSpans.isEmpty()) {
            final View view;
            final LayoutParams lp;
            int position;
            if (nextView != null) {
                view = nextView.view;
                lp = nextView.lp;
                position = nextView.position;
                nextView = null;
            } else if (layoutState.hasMore(state)) {
                view = layoutState.next(recycler);
                lp = ((LayoutParams) view.getLayoutParams());
                position = lp.getViewLayoutPosition();
                setupSpanSize(view, lp, position);
            } else {
                break;
            }
            if (preView == null) preView = findPreviousView(layoutState, state, position);

            final int spanIndex = mLazySpanLookup.getSpan(position);
            final boolean assignSpan = spanIndex == LayoutParams.INVALID_SPAN_ID;
            @Nullable List<Brother> brothers = null;
            if (lp.isAlignHalfSpan()) {
                int remainSize;
                if (assignSpan) {
                    remainSize = mSpanCount - lp.getSpanSize();
                } else {
                    final boolean preferLastSpan = preferLastSpan(layoutState.mLayoutDirection);
                    if (preferLastSpan) remainSize = spanIndex; else remainSize = mSpanCount - spanIndex - lp.getSpanSize();
                }
                if (remainSize > 0) while (layoutState.hasMore(state)) {
                    View v = layoutState.next(recycler);
                    LayoutParams l = ((LayoutParams) v.getLayoutParams());
                    int p = l.getViewLayoutPosition();
                    setupSpanSize(v, l, p);
                    remainSize -= l.getSpanSize();
                    if (l.isAlignHalfSpan() && remainSize >= 0) {
                        if (brothers == null) brothers = new ArrayList<>(mSpanCount);
                        brothers.add(Brother.get(v, l, p));
                        if (remainSize == 0) break;
                    } else {
                        nextView = Brother.get(v, l, p);
                        break;
                    }
                }
            }

            Span currentSpan; // 定义水平起始位置用
            if (assignSpan) {
                currentSpan = getNextSpan(layoutState, view, preView, brothers);
                mLazySpanLookup.setSpan(position, currentSpan);
                if (brothers != null) for (Brother brother : brothers) {
                    mLazySpanLookup.setSpan(brother.position, brother.currentSpan);
                }
                if (DEBUG) {
                    Log.d(TAG, "assigned " + currentSpan.mIndex + " for " + position);
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "using " + spanIndex + " for pos " + position);
                }
                currentSpan = mSpans[spanIndex];
                checkNextSpans(layoutState, spanIndex, lp.getSpanSize(), brothers); // mLazySpanLookup.setSpan
            }
            // assign span before measuring so that item decorators can get updated span index
            lp.mSpan = currentSpan;
            if (brothers != null) for (Brother brother : brothers) {
                brother.lp.mSpan = brother.currentSpan;
            }

            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                addView(view);
                if (brothers != null) for (Brother brother : brothers) {
                    addView(brother.view);
                }
            } else {
                addView(view, 0);
                if (brothers != null) for (Brother brother : brothers) {
                    addView(brother.view, 0);
                }
            }

            measureChildWithDecorationsAndMargin(view, lp, false);
            int maxSize = mPrimaryOrientation.getDecoratedMeasurement(view);

            if (brothers != null) for (Brother brother : brothers) {
                measureChildWithDecorationsAndMargin(brother.view, brother.lp, false);
                maxSize = Math.max(maxSize, mPrimaryOrientation.getDecoratedMeasurement(brother.view));
            }
            if (brothers != null) {
                if (maxSize != mPrimaryOrientation.getDecoratedMeasurement(view)) {
                    measureChildWithDecorationsAndMargin(view, lp, maxSize);
                }
                for (Brother brother : brothers) {
                    if (maxSize != mPrimaryOrientation.getDecoratedMeasurement(brother.view)) {
                        measureChildWithDecorationsAndMargin(brother.view, brother.lp, maxSize);
                    }
                }
            }


            final int start;
            final int end;
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                start = getAlignEndLine(defaultNewViewLine, layoutState, view, preView, brothers);
                end = start + mPrimaryOrientation.getDecoratedMeasurement(view);
                if (assignSpan && lp.isAlignSpan()) { // 记录其之上的间隙
                    LazySpanLookup.AlignSpanItem alignSpanItem = createAlignSpanItemFromEnd(start, lp);
                    if (alignSpanItem != null) {
                        alignSpanItem.mGapDir = LayoutState.LAYOUT_START;
                        alignSpanItem.mPosition = position;
                        mLazySpanLookup.addAlignSpanItem(alignSpanItem);
                    }
                }
                if (brothers != null) for (Brother brother : brothers) {
                    brother.end = brother.start + mPrimaryOrientation.getDecoratedMeasurement(brother.view);
                    if (assignSpan && lp.isAlignSpan()) {
                        LazySpanLookup.AlignSpanItem alignSpanItem = createAlignSpanItemFromEnd(brother.start, brother.lp);
                        if (alignSpanItem != null) {
                            alignSpanItem.mGapDir = LayoutState.LAYOUT_START;
                            alignSpanItem.mPosition = brother.position;
                            mLazySpanLookup.addAlignSpanItem(alignSpanItem);
                        }
                    }
                }
            } else {
                end = getAlignStartLine(defaultNewViewLine, layoutState, view, preView, brothers);
                start = end - mPrimaryOrientation.getDecoratedMeasurement(view);
                if (assignSpan && lp.isAlignSpan()) { // 记录其之下的间隙
                    LazySpanLookup.AlignSpanItem alignSpanItem = createAlignSpanItemFromStart(end, lp);
                    if (alignSpanItem != null) {
                        alignSpanItem.mGapDir = LayoutState.LAYOUT_END;
                        alignSpanItem.mPosition = position;
                        mLazySpanLookup.addAlignSpanItem(alignSpanItem);
                    }
                }
                if (brothers != null) for (Brother brother : brothers) {
                    brother.start = brother.end - mPrimaryOrientation.getDecoratedMeasurement(brother.view);
                    if (assignSpan && lp.isAlignSpan()) {
                        LazySpanLookup.AlignSpanItem alignSpanItem = createAlignSpanItemFromStart(brother.end, brother.lp);
                        if (alignSpanItem != null) {
                            alignSpanItem.mGapDir = LayoutState.LAYOUT_END;
                            alignSpanItem.mPosition = brother.position;
                            mLazySpanLookup.addAlignSpanItem(alignSpanItem);
                        }
                    }
                }
            }

            // check if this item may create gaps in the future
            if (lp.isAlignSpan() && layoutState.mItemDirection == LayoutState.ITEM_DIRECTION_HEAD) {
                if (assignSpan) {
                    mLaidOutInvalidAlignSpan = true;
                } else {
                    final boolean hasInvalidGap;
                    if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                        hasInvalidGap = !areAllEndsEqual();
                    } else { // layoutState.mLayoutDirection == LAYOUT_START
                        hasInvalidGap = !areAllStartsEqual();
                    }
                    if (hasInvalidGap) {
                        final LazySpanLookup.AlignSpanItem alignSpanItem = mLazySpanLookup.getFullSpanItem(position);
                        if (alignSpanItem != null) {
                            alignSpanItem.mHasUnwantedGapAfter = true;
                        }
                        mLaidOutInvalidAlignSpan = true;
                    }
                }
            }
            attachViewToSpans(view, lp, layoutState);
            if (brothers != null) for (Brother brother : brothers) {
                attachViewToSpans(brother.view, brother.lp, layoutState);
            }
            final int otherStart;
            final int otherEnd;
            if (isLayoutRTL() && mOrientation == VERTICAL) {
                otherEnd = mSecondaryOrientation.getEndAfterPadding() - (mSpanCount - lp.getSpanSize() - currentSpan.mIndex) * mSizePerSpan;
                otherStart = otherEnd - mSecondaryOrientation.getDecoratedMeasurement(view);
                if (brothers != null) for (Brother brother : brothers) {
                    brother.otherEnd = mSecondaryOrientation.getEndAfterPadding() - (mSpanCount - brother.lp.getSpanSize() - brother.currentSpan.mIndex) * mSizePerSpan;
                    brother.otherStart = brother.otherEnd - mSecondaryOrientation.getDecoratedMeasurement(brother.view);
                }
            } else {
                otherStart = currentSpan.mIndex * mSizePerSpan + mSecondaryOrientation.getStartAfterPadding();
                otherEnd = otherStart + mSecondaryOrientation.getDecoratedMeasurement(view);
                if (brothers != null) for (Brother brother : brothers) {
                    brother.otherStart = brother.currentSpan.mIndex * mSizePerSpan + mSecondaryOrientation.getStartAfterPadding();
                    brother.otherEnd = brother.otherStart + mSecondaryOrientation.getDecoratedMeasurement(brother.view);
                }
            }

            if (mOrientation == VERTICAL) {
                layoutDecoratedWithMargins(view, otherStart, start, otherEnd, end);
                if (brothers != null) for (Brother brother : brothers) {
                    layoutDecoratedWithMargins(brother.view, brother.otherStart, brother.start, brother.otherEnd, brother.end);
                }
            } else {
                layoutDecoratedWithMargins(view, start, otherStart, end, otherEnd);
                if (brothers != null) for (Brother brother : brothers) {
                    layoutDecoratedWithMargins(brother.view, brother.start, brother.otherStart, brother.end, brother.otherEnd);
                }
            }

            updateAllRemainingSpans(lp, mLayoutState.mLayoutDirection, targetLine);
            if (brothers != null) for (Brother brother : brothers) {
                updateAllRemainingSpans(brother.lp, mLayoutState.mLayoutDirection, targetLine);
            }

            recycle(recycler, mLayoutState);
            if (mLayoutState.mStopInFocusable && view.hasFocusable()) {
                clearRemainingSpans(lp);
            }
            added = true;
            preView = brothers == null ? view : brothers.get(brothers.size() - 1).view;
        }
        if (!added) {
            recycle(recycler, mLayoutState);
        }
        final int diff;
        if (mLayoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
            final int minStart = getMinStart(mPrimaryOrientation.getStartAfterPadding());
            diff = mPrimaryOrientation.getStartAfterPadding() - minStart;
        } else {
            final int maxEnd = getMaxEnd(mPrimaryOrientation.getEndAfterPadding());
            diff = maxEnd - mPrimaryOrientation.getEndAfterPadding();
        }
        return diff > 0 ? Math.min(layoutState.mAvailable, diff) : 0;
    }

    private void setupSpanSize(View view, LayoutParams lp, int position) {
        final int key = 0xFF << 22;
        final Object tag = view.getTag(key);
        if (tag instanceof Integer) {
            lp.mSpanSize = (int) tag;
        } else {
            final int spanSize = getSpanSize(position);
            lp.mSpanSize = spanSize;
            view.setTag(key, spanSize);
        }
    }

    private int getSpanSize(int position) {
        final boolean ss = mSpanSizeLookup.isStaggeredStyle(position);
        if (ss) {
            return -Math.abs(mSpanSizeLookup.getStaggeredSpanSize());
        } else {
            return Math.abs(mSpanSizeLookup.getGridSpanSize(position));
        }
    }

    private void clearRemainingSpans(LayoutParams lp) {
        final int spanIndex = lp.getSpanIndex();
        final int spanSize = lp.getSpanSize();
        int start = spanIndex;
        final int end = spanIndex + spanSize;
        for (; start != end; start++) {
            mRemainingSpans.set(start, false);
        }
    }

    @Nullable
    private LazySpanLookup.AlignSpanItem createAlignSpanItemFromEnd(int newItemTop, LayoutParams lp) {
        final int spanIndex = lp.getSpanIndex();
        final int spanSize = lp.getSpanSize();
        int start = spanIndex;
        final int end = spanIndex + spanSize;
        final int[] gapPerSpan = new int[mSpanCount];
        boolean useful = false;
        for (; start != end; start++) {
            final int i = newItemTop - mSpans[start].getEndLine(newItemTop);
            gapPerSpan[start] = i;
            if (i != 0) useful = true;
        }
        if (useful) {
            LazySpanLookup.AlignSpanItem fsi = new LazySpanLookup.AlignSpanItem();
            fsi.mGapPerSpan = gapPerSpan;
            return fsi;
        }
        return null;
    }

    @Nullable
    private LazySpanLookup.AlignSpanItem createAlignSpanItemFromStart(int newItemBottom, LayoutParams lp) {
        final int spanIndex = lp.getSpanIndex();
        final int spanSize = lp.getSpanSize();
        int start = spanIndex;
        final int end = spanIndex + spanSize;
        final int[] gapPerSpan = new int[mSpanCount];
        boolean useful = false;
        for (; start != end; start++) {
            final int i = mSpans[start].getStartLine(newItemBottom) - newItemBottom;
            gapPerSpan[start] = i;
            if (i != 0) useful = true;
        }
        if (useful) {
            LazySpanLookup.AlignSpanItem fsi = new LazySpanLookup.AlignSpanItem();
            fsi.mGapPerSpan = gapPerSpan;
            return fsi;
        }
        return null;
    }

    private void attachViewToSpans(View view, LayoutParams lp, LayoutState layoutState) {
        final int spanIndex = lp.getSpanIndex();
        final int spanSize = lp.getSpanSize();
        int start = spanIndex + spanSize - 1;
        final int end = spanIndex - 1;
        // note:: traverse in reverse so that we end up assigning full span items to 0
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
            for (; start != end; --start) {
                mSpans[start].appendToSpan(view);
            }
        } else {
            for (; start != end; --start) {
                mSpans[start].prependToSpan(view);
            }
        }
    }

    private void recycle(RecyclerView.Recycler recycler, LayoutState layoutState) {
        if (!layoutState.mRecycle || layoutState.mInfinite) {
            return;
        }
        if (layoutState.mAvailable == 0) {
            // easy, recycle line is still valid
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                recycleFromEnd(recycler, layoutState.mEndLine);
            } else {
                recycleFromStart(recycler, layoutState.mStartLine);
            }
        } else {
            // scrolling case, recycle line can be shifted by how much space we could cover
            // by adding new views
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                // calculate recycle line
                int scrolled = layoutState.mStartLine - getMaxStart(layoutState.mStartLine);
                final int line;
                if (scrolled < 0) {
                    line = layoutState.mEndLine;
                } else {
                    line = layoutState.mEndLine - Math.min(scrolled, layoutState.mAvailable);
                }
                recycleFromEnd(recycler, line);
            } else {
                // calculate recycle line
                int scrolled = getMinEnd(layoutState.mEndLine) - layoutState.mEndLine;
                final int line;
                if (scrolled < 0) {
                    line = layoutState.mStartLine;
                } else {
                    line = layoutState.mStartLine + Math.min(scrolled, layoutState.mAvailable);
                }
                recycleFromStart(recycler, line);
            }
        }
    }

    private void updateAllRemainingSpans(@Nullable LayoutParams lp, int layoutDir, int targetLine) {
        final int spanIndex = lp != null ? lp.getSpanIndex() : 0;
        final int spanSize = lp != null ? lp.getSpanSize() : mSpanCount;
        final int end = spanIndex + spanSize;
        for (int i = spanIndex; i != end; ++i) {
            if (mSpans[i].mViews.isEmpty()) {
                continue;
            }
            updateRemainingSpans(mSpans[i], layoutDir, targetLine);
        }
    }

    private void updateRemainingSpans(Span span, int layoutDir, int targetLine) {
        final int deletedSize = span.getDeletedSize();
        if (layoutDir == LayoutState.LAYOUT_START) {
            final int line = span.getStartLine();
            if (line + deletedSize <= targetLine) {
                mRemainingSpans.set(span.mIndex, false);
            }
        } else {
            final int line = span.getEndLine();
            if (line - deletedSize >= targetLine) {
                mRemainingSpans.set(span.mIndex, false);
            }
        }
    }

    private int getMaxStart(int def) {
        int maxStart = mSpans[0].getStartLine(def);
        for (int i = 1; i < mSpanCount; i++) {
            final int spanStart = mSpans[i].getStartLine(def);
            if (spanStart > maxStart) {
                maxStart = spanStart;
            }
        }
        return maxStart;
    }

    private int getMinStart(int def) {
        int minStart = mSpans[0].getStartLine(def);
        for (int i = 1; i < mSpanCount; i++) {
            final int spanStart = mSpans[i].getStartLine(def);
            if (spanStart < minStart) {
                minStart = spanStart;
            }
        }
        return minStart;
    }

    private int getMinStart(int def, LayoutParams lp) {
        final int spanIndex = lp.getSpanIndex();
        final int spanSize = lp.getSpanSize();
        int start = spanIndex + 1;
        final int end = spanIndex + spanSize;
        int minStart = mSpans[spanIndex].getStartLine(def);

        for (; start < end; start++) {
            final int spanStart = mSpans[start].getStartLine(def);
            if (spanStart < minStart) {
                minStart = spanStart;
            }
        }
        return minStart;
    }

    boolean areAllEndsEqual() {
        int end = mSpans[0].getEndLine(Span.INVALID_LINE);
        for (int i = 1; i < mSpanCount; i++) {
            if (mSpans[i].getEndLine(Span.INVALID_LINE) != end) {
                return false;
            }
        }
        return true;
    }

    boolean areAllStartsEqual() {
        int start = mSpans[0].getStartLine(Span.INVALID_LINE);
        for (int i = 1; i < mSpanCount; i++) {
            if (mSpans[i].getStartLine(Span.INVALID_LINE) != start) {
                return false;
            }
        }
        return true;
    }

    private int getAlignEndLine(int def, LayoutState layoutState, View view, @Nullable View preView, @Nullable List<Brother> brothers) {
        final LayoutParams lp = ((LayoutParams) view.getLayoutParams());
        if (lp.isFullSpan()) {
            return getMaxEnd(def);
        }
        if (lp.isAlignHalfSpan()) { // 当前是水平网络
            final int maxEnd = getMaxEnd(def);
            if (brothers != null) for (Brother brother : brothers) {
                brother.start = maxEnd;
            }
            return maxEnd;
        } else { // 当前是瀑布流
            if (preView == null) {
                return lp.mSpan.getEndLine(def);
            }
            final LayoutParams preLp = (LayoutParams) preView.getLayoutParams();
            if (preLp.isAlignSpan()) {
                return getMaxEnd(def); // 上一个水平
            }
            if (checkPreRowIfAlignGrid(layoutState)) { // 上一个也是瀑布流
                final int prePosition = preLp.getViewLayoutPosition();
                final int preSpanSize = preLp.getSpanSize();
                final int preSpanIndex = mLazySpanLookup.getSpan(prePosition);
                final int spanSize = lp.getSpanSize();
                final boolean preferLastSpan = preferLastSpan(layoutState.mLayoutDirection);
                final boolean sameLine;
                if (preferLastSpan) {
                    sameLine = (preSpanIndex - spanSize) >= 0;
                } else {
                    sameLine = (preSpanIndex + preSpanSize) + spanSize <= mSpanCount;
                }
                if (sameLine) {
                    return mPrimaryOrientation.getDecoratedStart(preView); // 瀑布流留在当前行, 是`getMaxEnd`(top)
                }
            }
            return lp.mSpan.getEndLine(def);
        }
    }

    /**
     * @return true 瀑布流需要水平分配
     */
    private boolean checkPreRowIfAlignGrid(LayoutState layoutState) {
        final boolean preferLastSpan = preferLastSpan(layoutState.mLayoutDirection);
        final int first = preferLastSpan ? mSpanCount - 1 : 0;
        final ArrayList<View> mViews = mSpans[first].mViews;
        final int size = mViews.size();
        final int preRowIndex = layoutState.mLayoutDirection == LayoutState.LAYOUT_END ? size - 2 : 1;
        if (preRowIndex < 0 || preRowIndex >= size) return false;
        final View view = mViews.get(preRowIndex);
        final LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
        return layoutParams.isAlignHalfSpan();
    }

    private int getMaxEnd(int def) {
        int maxEnd = mSpans[0].getEndLine(def);
        for (int i = 1; i < mSpanCount; i++) {
            final int spanEnd = mSpans[i].getEndLine(def);
            if (spanEnd > maxEnd) {
                maxEnd = spanEnd;
            }
        }
        return maxEnd;
    }

    private boolean checkStartIfStaggered(LayoutParams lp) {
        final int spanIndex = lp.getSpanIndex();
        final int spanSize = lp.getSpanSize();
        int start = spanIndex;
        final int end = spanIndex + spanSize;
        for (; start != end; start++) {
            final ArrayList<View> views = mSpans[start].mViews;
            if (views.size() > 0) {
                final LayoutParams layoutParams = (LayoutParams) views.get(0).getLayoutParams();
                if (layoutParams.isStaggeredSpan()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkStartIfStaggered(@Nullable List<Brother> brothers) {
        if (brothers != null) for (Brother brother : brothers) {
            if (checkStartIfStaggered(brother.lp)) {
                return true;
            }
        }
        return false;
    }

    private int getAlignStartLine(int def, LayoutState layoutState, View view, @Nullable View preView, @Nullable List<Brother> brothers) {
        final LayoutParams lp = ((LayoutParams) view.getLayoutParams());
        if (lp.isFullSpan()) {
            return getMinStart(def);
        }
        if (lp.isAlignHalfSpan()) { // 当前是水平网络
            final int minStart;
            if (checkStartIfStaggered(lp) || checkStartIfStaggered(brothers)) {
                minStart = getMinStart(def);
            } else {
                if (preView != null && ((LayoutParams) preView.getLayoutParams()).isAlignSpan()) {
                    minStart = mPrimaryOrientation.getDecoratedStart(preView);
                } else {
                    minStart = getMinStart(def, lp);
                }
            }
            if (brothers != null) for (Brother brother : brothers) {
                brother.end = minStart;
            }
            return minStart;
        } else { // 当前是瀑布流
            return getMinStart(def, lp);
        }
    }

    private int getMinEnd(int def) {
        int minEnd = mSpans[0].getEndLine(def);
        for (int i = 1; i < mSpanCount; i++) {
            final int spanEnd = mSpans[i].getEndLine(def);
            if (spanEnd < minEnd) {
                minEnd = spanEnd;
            }
        }
        return minEnd;
    }

    private void recycleFromStart(RecyclerView.Recycler recycler, int line) {
        while (getChildCount() > 0) {
            View child = getChildAt(0);
            if (mPrimaryOrientation.getDecoratedEnd(child) <= line
                    && mPrimaryOrientation.getTransformedEndWithDecoration(child) <= line) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                // Don't recycle the last View in a span not to lose span's start/end lines
                final int spanIndex = lp.getSpanIndex();
                final int spanSize = lp.getSpanSize();
                final int end = spanIndex + spanSize;
                for (int j = spanIndex; j != end; j++) {
                    if (mSpans[j].mViews.size() == 1) {
                        return;
                    }
                }
                for (int j = spanIndex; j != end; j++) {
                    mSpans[j].popStart();
                }
                removeAndRecycleView(child, recycler);
            } else {
                return; // done
            }
        }
    }

    private void recycleFromEnd(RecyclerView.Recycler recycler, int line) {
        final int childCount = getChildCount();
        int i;
        for (i = childCount - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (mPrimaryOrientation.getDecoratedStart(child) >= line
                    && mPrimaryOrientation.getTransformedStartWithDecoration(child) >= line) {
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                // Don't recycle the last View in a span not to lose span's start/end lines
                final int spanIndex = lp.getSpanIndex();
                final int spanSize = lp.getSpanSize();
                final int end = spanIndex + spanSize;
                for (int j = spanIndex; j != end; j++) {
                    if (mSpans[j].mViews.size() == 1) {
                        return;
                    }
                }
                for (int j = spanIndex; j != end; j++) {
                    mSpans[j].popEnd();
                }
                removeAndRecycleView(child, recycler);
            } else {
                return; // done
            }
        }
    }

    /**
     * @return True if last span is the first one we want to fill
     */
    private boolean preferLastSpan(int layoutDir) {
        if (mOrientation == HORIZONTAL) {
            return (layoutDir == LayoutState.LAYOUT_START) != mShouldReverseLayout;
        }
        return ((layoutDir == LayoutState.LAYOUT_START) == mShouldReverseLayout) == isLayoutRTL();
    }

    @Nullable
    private View findPreviousView(LayoutState layoutState, RecyclerView.State state, int position) {
        final int prePosition = position - layoutState.mLayoutDirection;
        if (prePosition >= 0 && prePosition < state.getItemCount()) {
            final int count = getChildCount();
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                for (int i = count - 1; i >= 0; i--) {
                    final View childAt = getChildAt(i);
                    final LayoutParams clp = (LayoutParams) childAt.getLayoutParams();
                    if (clp.getViewLayoutPosition() == prePosition) {
                        return childAt;
                    }
                }
            } else {
                for (int i = 0; i < count; i++) {
                    final View childAt = getChildAt(i);
                    final LayoutParams clp = (LayoutParams) childAt.getLayoutParams();
                    if (clp.getViewLayoutPosition() == prePosition) {
                        return childAt;
                    }
                }
            }
        }
        return null;
    }

    private void checkNextSpans(LayoutState layoutState, int preIndex, int preSize, @Nullable List<Brother> brothers) {
        if (brothers == null) return;
        final boolean preferLastSpan = preferLastSpan(layoutState.mLayoutDirection);
        for (Brother brother : brothers) {
            final int index;
            final int size = brother.lp.getSpanSize();
            if (preferLastSpan) {
                index = preIndex - size; // note: spans index 始终从左到右
            } else {
                index = preIndex + preSize;
            }
            brother.currentSpan = mSpans[index];
            mLazySpanLookup.setSpan(brother.position, brother.currentSpan);
            preIndex = index;
            preSize = size;
        }
    }

    /**
     * 解决 mIndex 的问题
     */
    private Span getNextSpan(LayoutState layoutState, View view, @Nullable View preView, @Nullable List<Brother> brothers) {
        final LayoutParams lp = ((LayoutParams) view.getLayoutParams());
        if (lp.isFullSpan()) {
            return mSpans[0];
        }
        final int spanSize = lp.getSpanSize();
        final boolean preferLastSpan = preferLastSpan(layoutState.mLayoutDirection);
        final int first = preferLastSpan ? mSpanCount - spanSize : 0;
        if (lp.isAlignHalfSpan()) { // 当前是水平网络
            int preIndex = first;
            int preSize = spanSize;
            if (brothers != null) for (Brother brother : brothers) {
                final int index;
                final int size = brother.lp.getSpanSize();
                if (preferLastSpan) {
                    index = preIndex - size; // note: spans index 始终从左到右
                } else {
                    index = preIndex + preSize;
                }
                brother.currentSpan = mSpans[index];
                preIndex = index;
                preSize = size;
            }
            return mSpans[first];
        } else { // 当前是瀑布流
            if (preView == null) {
                return getNextSpan(layoutState, lp);
            }
            final LayoutParams preLp = (LayoutParams) preView.getLayoutParams();
            if (preLp.isAlignSpan()) {
                return mSpans[first];
            }
            if (checkPreRowIfAlignGrid(layoutState)) { // 上一个也是瀑布流
                final int prePosition = preLp.getViewLayoutPosition();
                final int preSpanSize = preLp.getSpanSize();
                final int preSpanIndex = mLazySpanLookup.getSpan(prePosition);
                final int index; // note 当前 index >=1, 前一行是水平网格, 重新分配按水平
                final boolean sameLine;
                if (preferLastSpan) {
                    index = preSpanIndex - spanSize;
                    sameLine = index >= 0;
                } else {
                    index = preSpanIndex + preSpanSize;
                    sameLine = index + spanSize <= mSpanCount;
                }
                if (sameLine) {
                    return mSpans[index];
                }
            }
            return getNextSpan(layoutState, lp);
        }
    }

    /**
     * Finds the span for the next view.
     */
    private Span getNextSpan(LayoutState layoutState, LayoutParams lp) {
        final boolean preferLastSpan = preferLastSpan(layoutState.mLayoutDirection);
        final int spanSize = lp.getSpanSize();
        final int step = preferLastSpan ? -spanSize : spanSize;
        int i = preferLastSpan ? mSpanCount - spanSize : 0;
        int times = mSpanCount / spanSize;
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
            Span min = null;
            int minLine = Integer.MAX_VALUE;
            final int defaultLine = mPrimaryOrientation.getStartAfterPadding();
            for (; times != 0; i += step, times--) {
                final Span other = mSpans[i];
                int otherLine = other.getEndLine(defaultLine);
                if (otherLine < minLine) {
                    min = other;
                    minLine = otherLine;
                }
            }
            return min;
        } else {
            Span max = null;
            int maxLine = Integer.MIN_VALUE;
            final int defaultLine = mPrimaryOrientation.getEndAfterPadding();
            for (; times != 0; i += step, times--) {
                final Span other = mSpans[i];
                int otherLine = other.getStartLine(defaultLine);
                if (otherLine > maxLine) {
                    max = other;
                    maxLine = otherLine;
                }
            }
            return max;
        }
    }

    @Override
    public boolean canScrollVertically() {
        return mOrientation == VERTICAL;
    }

    @Override
    public boolean canScrollHorizontally() {
        return mOrientation == HORIZONTAL;
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler,
                                    RecyclerView.State state) {
        return scrollBy(dx, recycler, state);
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
                                  RecyclerView.State state) {
        return scrollBy(dy, recycler, state);
    }

    private int calculateScrollDirectionForPosition(int position) {
        if (getChildCount() == 0) {
            return mShouldReverseLayout ? LayoutState.LAYOUT_END : LayoutState.LAYOUT_START;
        }
        final int firstChildPos = getFirstChildPosition();
        return position < firstChildPos != mShouldReverseLayout ? LayoutState.LAYOUT_START : LayoutState.LAYOUT_END;
    }

    @Override
    public PointF computeScrollVectorForPosition(int targetPosition) {
        final int direction = calculateScrollDirectionForPosition(targetPosition);
        if (direction == 0) {
            return null;
        }
        PointF outVector = new PointF();
        if (mOrientation == HORIZONTAL) {
            outVector.x = direction;
            outVector.y = 0;
        } else {
            outVector.x = 0;
            outVector.y = direction;
        }
        return outVector;
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state,
                                       int position) {
        LinearSmoothScroller scroller = new LinearSmoothScroller(recyclerView.getContext());
        scroller.setTargetPosition(position);
        startSmoothScroll(scroller);
    }

    @Override
    public void scrollToPosition(int position) {
        if (mPendingSavedState != null && mPendingSavedState.mAnchorPosition != position) {
            mPendingSavedState.invalidateAnchorPositionInfo();
        }
        mPendingScrollPosition = position;
        mPendingScrollPositionOffset = INVALID_OFFSET;
        requestLayout();
    }

    /**
     * Scroll to the specified adapter position with the given offset from layout start.
     * <p>
     * Note that scroll position change will not be reflected until the next layout call.
     * <p>
     * If you are just trying to make a position visible, use {@link #scrollToPosition(int)}.
     *
     * @param position Index (starting at 0) of the reference item.
     * @param offset   The distance (in pixels) between the start edge of the item view and
     *                 start edge of the RecyclerView.
     * @see #setReverseLayout(boolean)
     * @see #scrollToPosition(int)
     */
    public void scrollToPositionWithOffset(int position, int offset) {
        if (mPendingSavedState != null) {
            mPendingSavedState.invalidateAnchorPositionInfo();
        }
        mPendingScrollPosition = position;
        mPendingScrollPositionOffset = offset;
        requestLayout();
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(LIBRARY)
    public void collectAdjacentPrefetchPositions(int dx, int dy, RecyclerView.State state,
                                                 LayoutPrefetchRegistry layoutPrefetchRegistry) {
        /* This method uses the simplifying assumption that the next N items (where N = span count)
         * will be assigned, one-to-one, to spans, where ordering is based on which span  extends
         * least beyond the viewport.
         *
         * While this simplified model will be incorrect in some cases, it's difficult to know
         * item heights, or whether individual items will be full span prior to construction.
         *
         * While this greedy estimation approach may underestimate the distance to prefetch items,
         * it's very unlikely to overestimate them, so distances can be conservatively used to know
         * the soonest (in terms of scroll distance) a prefetched view may come on screen.
         */
        int delta = (mOrientation == HORIZONTAL) ? dx : dy;
        if (getChildCount() == 0 || delta == 0) {
            // can't support this scroll, so don't bother prefetching
            return;
        }
        prepareLayoutStateForDelta(delta, state);

        // build sorted list of distances to end of each span (though we don't care which is which)
        if (mPrefetchDistances == null || mPrefetchDistances.length < mSpanCount) {
            mPrefetchDistances = new int[mSpanCount];
        }

        int itemPrefetchCount = 0;
        for (int i = 0; i < mSpanCount; i++) {
            // compute number of pixels past the edge of the viewport that the current span extends
            int distance = mLayoutState.mItemDirection == LayoutState.LAYOUT_START
                    ? mLayoutState.mStartLine - mSpans[i].getStartLine(mLayoutState.mStartLine)
                    : mSpans[i].getEndLine(mLayoutState.mEndLine) - mLayoutState.mEndLine;
            if (distance >= 0) {
                // span extends to the edge, so prefetch next item
                mPrefetchDistances[itemPrefetchCount] = distance;
                itemPrefetchCount++;
            }
        }
        Arrays.sort(mPrefetchDistances, 0, itemPrefetchCount);

        // then assign them in order to the next N views (where N = span count)
        for (int i = 0; i < itemPrefetchCount && mLayoutState.hasMore(state); i++) {
            layoutPrefetchRegistry.addPosition(mLayoutState.mCurrentPosition,
                    mPrefetchDistances[i]);
            mLayoutState.mCurrentPosition += mLayoutState.mItemDirection;
        }
    }

    void prepareLayoutStateForDelta(int delta, RecyclerView.State state) {
        final int referenceChildPosition;
        final int layoutDir;
        if (delta > 0) { // layout towards end
            layoutDir = LayoutState.LAYOUT_END;
            referenceChildPosition = getLastChildPosition();
        } else {
            layoutDir = LayoutState.LAYOUT_START;
            referenceChildPosition = getFirstChildPosition();
        }
        mLayoutState.mRecycle = true;
        updateLayoutState(referenceChildPosition, state);
        setLayoutStateDirection(layoutDir);
        mLayoutState.mCurrentPosition = referenceChildPosition + mLayoutState.mItemDirection;
        mLayoutState.mAvailable = Math.abs(delta);
    }

    int scrollBy(int dt, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0 || dt == 0) {
            return 0;
        }

        prepareLayoutStateForDelta(dt, state);
        int consumed = fill(recycler, mLayoutState, state);
        final int available = mLayoutState.mAvailable;
        final int totalScroll;
        if (available < consumed) {
            totalScroll = dt;
        } else if (dt < 0) {
            totalScroll = -consumed;
        } else { // dt > 0
            totalScroll = consumed;
        }
        if (DEBUG) {
            Log.d(TAG, "asked " + dt + " scrolled" + totalScroll);
        }

        mPrimaryOrientation.offsetChildren(-totalScroll);
        // always reset this if we scroll for a proper save instance state
        mLastLayoutFromEnd = mShouldReverseLayout;
        mLayoutState.mAvailable = 0;
        recycle(recycler, mLayoutState);
        return totalScroll;
    }

    int getLastChildPosition() {
        final int childCount = getChildCount();
        return childCount == 0 ? 0 : getPosition(getChildAt(childCount - 1));
    }

    int getFirstChildPosition() {
        final int childCount = getChildCount();
        return childCount == 0 ? 0 : getPosition(getChildAt(0));
    }

    /**
     * Finds the first View that can be used as an anchor View.
     *
     * @return Position of the View or 0 if it cannot find any such View.
     */
    private int findFirstReferenceChildPosition(int itemCount) {
        final int limit = getChildCount();
        for (int i = 0; i < limit; i++) {
            final View view = getChildAt(i);
            final int position = getPosition(view);
            if (position >= 0 && position < itemCount) {
                return position;
            }
        }
        return 0;
    }

    /**
     * Finds the last View that can be used as an anchor View.
     *
     * @return Position of the View or 0 if it cannot find any such View.
     */
    private int findLastReferenceChildPosition(int itemCount) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            final View view = getChildAt(i);
            final int position = getPosition(view);
            if (position >= 0 && position < itemCount) {
                return position;
            }
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        if (mOrientation == HORIZONTAL) {
            return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, () -> mSpanCount);
        } else {
            return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, () -> mSpanCount);
        }
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs, () -> mSpanCount);
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            return new LayoutParams((ViewGroup.MarginLayoutParams) lp, () -> mSpanCount);
        } else {
            return new LayoutParams(lp, () -> mSpanCount);
        }
    }

    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        return lp instanceof LayoutParams;
    }

    public int getOrientation() {
        return mOrientation;
    }

    @Nullable
    @Override
    public View onFocusSearchFailed(View focused, int direction, RecyclerView.Recycler recycler,
                                    RecyclerView.State state) {
        if (getChildCount() == 0) {
            return null;
        }

        final View directChild = findContainingItemView(focused);
        if (directChild == null) {
            return null;
        }

        resolveShouldLayoutReverse();
        final int layoutDir = convertFocusDirectionToLayoutDirection(direction);
        if (layoutDir == LayoutState.INVALID_LAYOUT) {
            return null;
        }
        LayoutParams prevFocusLayoutParams = (LayoutParams) directChild.getLayoutParams();
        boolean prevFocusFullSpan = prevFocusLayoutParams.isFullSpan();
        final Span prevFocusSpan = prevFocusLayoutParams.mSpan;
        final int referenceChildPosition;
        if (layoutDir == LayoutState.LAYOUT_END) { // layout towards end
            referenceChildPosition = getLastChildPosition();
        } else {
            referenceChildPosition = getFirstChildPosition();
        }
        updateLayoutState(referenceChildPosition, state);
        setLayoutStateDirection(layoutDir);

        mLayoutState.mCurrentPosition = referenceChildPosition + mLayoutState.mItemDirection;
        mLayoutState.mAvailable = (int) (MAX_SCROLL_FACTOR * mPrimaryOrientation.getTotalSpace());
        mLayoutState.mStopInFocusable = true;
        mLayoutState.mRecycle = false;
        fill(recycler, mLayoutState, state);
        mLastLayoutFromEnd = mShouldReverseLayout;
        if (!prevFocusFullSpan) {
            View view = prevFocusSpan.getFocusableViewAfter(referenceChildPosition, layoutDir);
            if (view != null && view != directChild) {
                return view;
            }
        }

        // either could not find from the desired span or prev view is full span.
        // traverse all spans
        if (preferLastSpan(layoutDir)) {
            for (int i = mSpanCount - 1; i >= 0; i--) {
                View view = mSpans[i].getFocusableViewAfter(referenceChildPosition, layoutDir);
                if (view != null && view != directChild) {
                    return view;
                }
            }
        } else {
            for (int i = 0; i < mSpanCount; i++) {
                View view = mSpans[i].getFocusableViewAfter(referenceChildPosition, layoutDir);
                if (view != null && view != directChild) {
                    return view;
                }
            }
        }

        // Could not find any focusable views from any of the existing spans. Now start the search
        // to find the best unfocusable candidate to become visible on the screen next. The search
        // is done in the same fashion: first, check the views in the desired span and if no
        // candidate is found, traverse the views in all the remaining spans.
        boolean shouldSearchFromStart = !mReverseLayout == (layoutDir == LayoutState.LAYOUT_START);
        View unfocusableCandidate = null;
        if (!prevFocusFullSpan) {
            unfocusableCandidate = findViewByPosition(shouldSearchFromStart
                    ? prevFocusSpan.findFirstPartiallyVisibleItemPosition() :
                    prevFocusSpan.findLastPartiallyVisibleItemPosition());
            if (unfocusableCandidate != null && unfocusableCandidate != directChild) {
                return unfocusableCandidate;
            }
        }

        if (preferLastSpan(layoutDir)) {
            for (int i = mSpanCount - 1; i >= 0; i--) {
                if (i == prevFocusSpan.mIndex) {
                    continue;
                }
                unfocusableCandidate = findViewByPosition(shouldSearchFromStart
                        ? mSpans[i].findFirstPartiallyVisibleItemPosition() :
                        mSpans[i].findLastPartiallyVisibleItemPosition());
                if (unfocusableCandidate != null && unfocusableCandidate != directChild) {
                    return unfocusableCandidate;
                }
            }
        } else {
            for (int i = 0; i < mSpanCount; i++) {
                unfocusableCandidate = findViewByPosition(shouldSearchFromStart
                        ? mSpans[i].findFirstPartiallyVisibleItemPosition() :
                        mSpans[i].findLastPartiallyVisibleItemPosition());
                if (unfocusableCandidate != null && unfocusableCandidate != directChild) {
                    return unfocusableCandidate;
                }
            }
        }
        return null;
    }

    /**
     * Converts a focusDirection to orientation.
     *
     * @param focusDirection One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
     *                       {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
     *                       {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
     *                       or 0 for not applicable
     * @return {@link LayoutState#LAYOUT_START} or {@link LayoutState#LAYOUT_END} if focus direction
     * is applicable to current state, {@link LayoutState#INVALID_LAYOUT} otherwise.
     */
    private int convertFocusDirectionToLayoutDirection(int focusDirection) {
        switch (focusDirection) {
            case View.FOCUS_BACKWARD:
                if (mOrientation == VERTICAL) {
                    return LayoutState.LAYOUT_START;
                } else if (isLayoutRTL()) {
                    return LayoutState.LAYOUT_END;
                } else {
                    return LayoutState.LAYOUT_START;
                }
            case View.FOCUS_FORWARD:
                if (mOrientation == VERTICAL) {
                    return LayoutState.LAYOUT_END;
                } else if (isLayoutRTL()) {
                    return LayoutState.LAYOUT_START;
                } else {
                    return LayoutState.LAYOUT_END;
                }
            case View.FOCUS_UP:
                return mOrientation == VERTICAL ? LayoutState.LAYOUT_START
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_DOWN:
                return mOrientation == VERTICAL ? LayoutState.LAYOUT_END
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_LEFT:
                return mOrientation == HORIZONTAL ? LayoutState.LAYOUT_START
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_RIGHT:
                return mOrientation == HORIZONTAL ? LayoutState.LAYOUT_END
                        : LayoutState.INVALID_LAYOUT;
            default:
                if (DEBUG) {
                    Log.d(TAG, "Unknown focus request:" + focusDirection);
                }
                return LayoutState.INVALID_LAYOUT;
        }

    }

    /**
     * LayoutParams used by StaggeredGridLayoutManager.
     * <p>
     * Note that if the orientation is {@link #VERTICAL}, the width parameter is ignored and if the
     * orientation is {@link #HORIZONTAL} the height parameter is ignored because child view is
     * expected to fill all of the space given to it.
     */
    public static class LayoutParams extends RecyclerView.LayoutParams {

        /**
         * Span Id for Views that are not laid out yet.
         */
        public static final int INVALID_SPAN_ID = -1;

        // Package scope to be able to access from tests.
        private Span mSpan;

        /**
         * NOTE 水平对齐半跨度
         * `负数值`表示原始瀑布流
         * `spanSize`<`spanCount` 水平窗格
         * 全跨度, `spanSize`=`spanCount`
         * 保留`0`
         */
        private int mSpanSize = 0;
        private final Supplier<Integer> mSpanCountSupplier;

        public LayoutParams(Context c, AttributeSet attrs, Supplier<Integer> spanCountSupplier) {
            super(c, attrs);
            mSpanCountSupplier = spanCountSupplier;
        }

        public LayoutParams(int width, int height, Supplier<Integer> spanCountSupplier) {
            super(width, height);
            mSpanCountSupplier = spanCountSupplier;
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source, Supplier<Integer> spanCountSupplier) {
            super(source);
            mSpanCountSupplier = spanCountSupplier;
        }

        public LayoutParams(ViewGroup.LayoutParams source, Supplier<Integer> spanCountSupplier) {
            super(source);
            mSpanCountSupplier = spanCountSupplier;
        }

        public LayoutParams(RecyclerView.LayoutParams source, Supplier<Integer> spanCountSupplier) {
            super(source);
            mSpanCountSupplier = spanCountSupplier;
        }

        public boolean isFullSpan() {
            return mSpanSize == mSpanCountSupplier.get();
        }

        public boolean isAlignSpan() {
            return mSpanSize > 0;
        }

        public boolean isAlignHalfSpan() {
            return mSpanSize > 0 && mSpanSize < mSpanCountSupplier.get();
        }

        public boolean isStaggeredSpan() {
            return mSpanSize < 0;
        }

        public final int getSpanSize() {
            return Math.max(1, Math.abs(mSpanSize));
        }

        /**
         * Returns the Span index to which this View is assigned.
         *
         * @return The Span index of the View. If View is not yet assigned to any span, returns
         * {@link #INVALID_SPAN_ID}.
         */
        public final int getSpanIndex() {
            if (mSpan == null) {
                return INVALID_SPAN_ID;
            }
            return mSpan.mIndex;
        }
    }

    // Package scoped to access from tests.
    class Span {

        static final int INVALID_LINE = Integer.MIN_VALUE;
        ArrayList<View> mViews = new ArrayList<>();
        int mCachedStart = INVALID_LINE;
        int mCachedEnd = INVALID_LINE;
        int mDeletedSize = 0;
        final int mIndex;

        Span(int index) {
            mIndex = index;
        }

        int getStartLine(int def) {
            if (mCachedStart != INVALID_LINE) {
                return mCachedStart;
            }
            if (mViews.size() == 0) {
                return def;
            }
            calculateCachedStart();
            return mCachedStart;
        }

        void calculateCachedStart() {
            final View startView = mViews.get(0);
            mCachedStart = calculateViewStart(startView);
        }

        int calculateViewStart(View startView) {
            int mCachedStart;
            final LayoutParams lp = getLayoutParams(startView);
            mCachedStart = mPrimaryOrientation.getDecoratedStart(startView);
            if (lp.isAlignSpan()) {
                LazySpanLookup.AlignSpanItem fsi = mLazySpanLookup.getFullSpanItem(lp.getViewLayoutPosition());
                if (fsi != null && fsi.mGapDir == LayoutState.LAYOUT_START) {
                    mCachedStart -= fsi.getGapForSpan(mIndex);
                }
            }
            return mCachedStart;
        }

        // Use this one when default value does not make sense and not having a value means a bug.
        int getStartLine() {
            if (mCachedStart != INVALID_LINE) {
                return mCachedStart;
            }
            calculateCachedStart();
            return mCachedStart;
        }

        int getEndLine(int def) {
            if (mCachedEnd != INVALID_LINE) {
                return mCachedEnd;
            }
            final int size = mViews.size();
            if (size == 0) {
                return def;
            }
            calculateCachedEnd();
            return mCachedEnd;
        }

        void calculateCachedEnd() {
            final View endView = mViews.get(mViews.size() - 1);
            mCachedEnd = calculateViewEnd(endView);
        }

        int calculateViewEnd(View endView) {
            int mCachedEnd;
            final LayoutParams lp = getLayoutParams(endView);
            mCachedEnd = mPrimaryOrientation.getDecoratedEnd(endView);
            if (lp.isAlignSpan()) {
                LazySpanLookup.AlignSpanItem fsi = mLazySpanLookup.getFullSpanItem(lp.getViewLayoutPosition());
                if (fsi != null && fsi.mGapDir == LayoutState.LAYOUT_END) {
                    mCachedEnd += fsi.getGapForSpan(mIndex);
                }
            }
            return mCachedEnd;
        }

        // Use this one when default value does not make sense and not having a value means a bug.
        int getEndLine() {
            if (mCachedEnd != INVALID_LINE) {
                return mCachedEnd;
            }
            calculateCachedEnd();
            return mCachedEnd;
        }

        void prependToSpan(View view) {
            LayoutParams lp = getLayoutParams(view);
            lp.mSpan = this;
            mViews.add(0, view);
            mCachedStart = INVALID_LINE;
            if (mViews.size() == 1) {
                mCachedEnd = INVALID_LINE;
            }
            if (lp.isItemRemoved() || lp.isItemChanged()) {
                mDeletedSize += mPrimaryOrientation.getDecoratedMeasurement(view);
            }
        }

        void appendToSpan(View view) {
            LayoutParams lp = getLayoutParams(view);
            lp.mSpan = this;
            mViews.add(view);
            mCachedEnd = INVALID_LINE;
            if (mViews.size() == 1) {
                mCachedStart = INVALID_LINE;
            }
            if (lp.isItemRemoved() || lp.isItemChanged()) {
                mDeletedSize += mPrimaryOrientation.getDecoratedMeasurement(view);
            }
        }

        // Useful method to preserve positions on a re-layout.
        void cacheReferenceLineAndClear(boolean reverseLayout, int offset) {
            int reference;
            if (reverseLayout) {
                reference = getEndLine(INVALID_LINE);
            } else {
                reference = getStartLine(INVALID_LINE);
            }
            clear();
            if (reference == INVALID_LINE) {
                return;
            }
            if ((reverseLayout && reference < mPrimaryOrientation.getEndAfterPadding())
                    || (!reverseLayout && reference > mPrimaryOrientation.getStartAfterPadding())) {
                return;
            }
            if (offset != INVALID_OFFSET) {
                reference += offset;
            }
            mCachedStart = mCachedEnd = reference;
        }

        void clear() {
            mViews.clear();
            invalidateCache();
            mDeletedSize = 0;
        }

        void invalidateCache() {
            mCachedStart = INVALID_LINE;
            mCachedEnd = INVALID_LINE;
        }

        void setLine(int line) {
            mCachedEnd = mCachedStart = line;
        }

        void popEnd() {
            final int size = mViews.size();
            View end = mViews.remove(size - 1);
            final LayoutParams lp = getLayoutParams(end);
            lp.mSpan = null;
            if (lp.isItemRemoved() || lp.isItemChanged()) {
                mDeletedSize -= mPrimaryOrientation.getDecoratedMeasurement(end);
            }
            if (size == 1) {
                mCachedStart = INVALID_LINE;
            }
            mCachedEnd = INVALID_LINE;
        }

        void popStart() {
            View start = mViews.remove(0);
            final LayoutParams lp = getLayoutParams(start);
            lp.mSpan = null;
            if (mViews.size() == 0) {
                mCachedEnd = INVALID_LINE;
            }
            if (lp.isItemRemoved() || lp.isItemChanged()) {
                mDeletedSize -= mPrimaryOrientation.getDecoratedMeasurement(start);
            }
            mCachedStart = INVALID_LINE;
        }

        public int getDeletedSize() {
            return mDeletedSize;
        }

        LayoutParams getLayoutParams(View view) {
            return (LayoutParams) view.getLayoutParams();
        }

        void onOffset(int dt) {
            if (mCachedStart != INVALID_LINE) {
                mCachedStart += dt;
            }
            if (mCachedEnd != INVALID_LINE) {
                mCachedEnd += dt;
            }
        }

        public int findFirstVisibleItemPosition() {
            return mReverseLayout
                    ? findOneVisibleChild(mViews.size() - 1, -1, false)
                    : findOneVisibleChild(0, mViews.size(), false);
        }

        public int findFirstPartiallyVisibleItemPosition() {
            return mReverseLayout
                    ? findOnePartiallyVisibleChild(mViews.size() - 1, -1, true)
                    : findOnePartiallyVisibleChild(0, mViews.size(), true);
        }

        public int findFirstCompletelyVisibleItemPosition() {
            return mReverseLayout
                    ? findOneVisibleChild(mViews.size() - 1, -1, true)
                    : findOneVisibleChild(0, mViews.size(), true);
        }

        public int findLastVisibleItemPosition() {
            return mReverseLayout
                    ? findOneVisibleChild(0, mViews.size(), false)
                    : findOneVisibleChild(mViews.size() - 1, -1, false);
        }

        public int findLastPartiallyVisibleItemPosition() {
            return mReverseLayout
                    ? findOnePartiallyVisibleChild(0, mViews.size(), true)
                    : findOnePartiallyVisibleChild(mViews.size() - 1, -1, true);
        }

        public int findLastCompletelyVisibleItemPosition() {
            return mReverseLayout
                    ? findOneVisibleChild(0, mViews.size(), true)
                    : findOneVisibleChild(mViews.size() - 1, -1, true);
        }

        /**
         * Returns the first view within this span that is partially or fully visible. Partially
         * visible refers to a view that overlaps but is not fully contained within RV's padded
         * bounded area. This view returned can be defined to have an area of overlap strictly
         * greater than zero if acceptEndPointInclusion is false. If true, the view's endpoint
         * inclusion is enough to consider it partially visible. The latter case can then refer to
         * an out-of-bounds view positioned right at the top (or bottom) boundaries of RV's padded
         * area. This is used e.g. inside
         * {@link #onFocusSearchFailed(View, int, RecyclerView.Recycler, RecyclerView.State)} for
         * calculating the next unfocusable child to become visible on the screen.
         *
         * @param fromIndex               The child position index to start the search from.
         * @param toIndex                 The child position index to end the search at.
         * @param completelyVisible       True if we have to only consider completely visible views,
         *                                false otherwise.
         * @param acceptCompletelyVisible True if we can consider both partially or fully visible
         *                                views, false, if only a partially visible child should be
         *                                returned.
         * @param acceptEndPointInclusion If the view's endpoint intersection with RV's padded
         *                                bounded area is enough to consider it partially visible,
         *                                false otherwise
         * @return The adapter position of the first view that's either partially or fully visible.
         * {@link RecyclerView#NO_POSITION} if no such view is found.
         */
        int findOnePartiallyOrCompletelyVisibleChild(int fromIndex, int toIndex,
                                                     boolean completelyVisible,
                                                     boolean acceptCompletelyVisible,
                                                     boolean acceptEndPointInclusion) {
            final int start = mPrimaryOrientation.getStartAfterPadding();
            final int end = mPrimaryOrientation.getEndAfterPadding();
            final int next = toIndex > fromIndex ? 1 : -1;
            for (int i = fromIndex; i != toIndex; i += next) {
                final View child = mViews.get(i);
                final int childStart = mPrimaryOrientation.getDecoratedStart(child);
                final int childEnd = mPrimaryOrientation.getDecoratedEnd(child);
                boolean childStartInclusion = acceptEndPointInclusion ? (childStart <= end)
                        : (childStart < end);
                boolean childEndInclusion = acceptEndPointInclusion ? (childEnd >= start)
                        : (childEnd > start);
                if (childStartInclusion && childEndInclusion) {
                    if (completelyVisible && acceptCompletelyVisible) {
                        // the child has to be completely visible to be returned.
                        if (childStart >= start && childEnd <= end) {
                            return getPosition(child);
                        }
                    } else if (acceptCompletelyVisible) {
                        // can return either a partially or completely visible child.
                        return getPosition(child);
                    } else if (childStart < start || childEnd > end) {
                        // should return a partially visible child if exists and a completely
                        // visible child is not acceptable in this case.
                        return getPosition(child);
                    }
                }
            }
            return RecyclerView.NO_POSITION;
        }

        int findOneVisibleChild(int fromIndex, int toIndex, boolean completelyVisible) {
            return findOnePartiallyOrCompletelyVisibleChild(fromIndex, toIndex, completelyVisible,
                    true, false);
        }

        int findOnePartiallyVisibleChild(int fromIndex, int toIndex,
                                         boolean acceptEndPointInclusion) {
            return findOnePartiallyOrCompletelyVisibleChild(fromIndex, toIndex, false, false,
                    acceptEndPointInclusion);
        }

        /**
         * Depending on the layout direction, returns the View that is after the given position.
         */
        public View getFocusableViewAfter(int referenceChildPosition, int layoutDir) {
            View candidate = null;
            if (layoutDir == LayoutState.LAYOUT_START) {
                final int limit = mViews.size();
                for (int i = 0; i < limit; i++) {
                    final View view = mViews.get(i);
                    if ((mReverseLayout && getPosition(view) <= referenceChildPosition)
                            || (!mReverseLayout && getPosition(view) >= referenceChildPosition)) {
                        break;
                    }
                    if (view.hasFocusable()) {
                        candidate = view;
                    } else {
                        break;
                    }
                }
            } else {
                for (int i = mViews.size() - 1; i >= 0; i--) {
                    final View view = mViews.get(i);
                    if ((mReverseLayout && getPosition(view) >= referenceChildPosition)
                            || (!mReverseLayout && getPosition(view) <= referenceChildPosition)) {
                        break;
                    }
                    if (view.hasFocusable()) {
                        candidate = view;
                    } else {
                        break;
                    }
                }
            }
            return candidate;
        }
    }

    /**
     * An array of mappings from adapter position to span.
     * This only grows when a write happens and it grows up to the size of the adapter.
     */
    static class LazySpanLookup {

        private static final int MIN_SIZE = 10;
        int[] mData;
        List<AlignSpanItem> mAlignSpanItems; // todo use map


        /**
         * Invalidates everything after this position, including full span information
         */
        int forceInvalidateAfter(int position) {
            if (mAlignSpanItems != null) {
                for (int i = mAlignSpanItems.size() - 1; i >= 0; i--) {
                    AlignSpanItem fsi = mAlignSpanItems.get(i);
                    if (fsi.mPosition >= position) {
                        mAlignSpanItems.remove(i);
                    }
                }
            }
            return invalidateAfter(position);
        }

        /**
         * returns end position for invalidation.
         */
        int invalidateAfter(int position) {
            if (mData == null) {
                return RecyclerView.NO_POSITION;
            }
            if (position >= mData.length) {
                return RecyclerView.NO_POSITION;
            }
            int endPosition = invalidateFullSpansAfter(position);
            if (endPosition == RecyclerView.NO_POSITION) {
                Arrays.fill(mData, position, mData.length, LayoutParams.INVALID_SPAN_ID);
                return mData.length;
            } else {
                // just invalidate items in between
                Arrays.fill(mData, position, endPosition + 1, LayoutParams.INVALID_SPAN_ID);
                return endPosition + 1;
            }
        }

        int getSpan(int position) {
            if (mData == null || position >= mData.length) {
                return LayoutParams.INVALID_SPAN_ID;
            } else {
                return mData[position];
            }
        }

        void setSpan(int position, Span span) {
            ensureSize(position);
            mData[position] = span.mIndex;
        }

        int sizeForPosition(int position) {
            int len = mData.length;
            while (len <= position) {
                len *= 2;
            }
            return len;
        }

        void ensureSize(int position) {
            if (mData == null) {
                mData = new int[Math.max(position, MIN_SIZE) + 1];
                Arrays.fill(mData, LayoutParams.INVALID_SPAN_ID);
            } else if (position >= mData.length) {
                int[] old = mData;
                mData = new int[sizeForPosition(position)];
                System.arraycopy(old, 0, mData, 0, old.length);
                Arrays.fill(mData, old.length, mData.length, LayoutParams.INVALID_SPAN_ID);
            }
        }

        void clear() {
            if (mData != null) {
                Arrays.fill(mData, LayoutParams.INVALID_SPAN_ID);
            }
            mAlignSpanItems = null;
        }

        void offsetForRemoval(int positionStart, int itemCount) {
            if (mData == null || positionStart >= mData.length) {
                return;
            }
            ensureSize(positionStart + itemCount);
            System.arraycopy(mData, positionStart + itemCount, mData, positionStart,
                    mData.length - positionStart - itemCount);
            Arrays.fill(mData, mData.length - itemCount, mData.length,
                    LayoutParams.INVALID_SPAN_ID);
            offsetFullSpansForRemoval(positionStart, itemCount);
        }

        private void offsetFullSpansForRemoval(int positionStart, int itemCount) {
            if (mAlignSpanItems == null) {
                return;
            }
            final int end = positionStart + itemCount;
            for (int i = mAlignSpanItems.size() - 1; i >= 0; i--) {
                AlignSpanItem fsi = mAlignSpanItems.get(i);
                if (fsi.mPosition < positionStart) {
                    continue;
                }
                if (fsi.mPosition < end) {
                    mAlignSpanItems.remove(i);
                } else {
                    fsi.mPosition -= itemCount;
                }
            }
        }

        void offsetForAddition(int positionStart, int itemCount) {
            if (mData == null || positionStart >= mData.length) {
                return;
            }
            ensureSize(positionStart + itemCount);
            System.arraycopy(mData, positionStart, mData, positionStart + itemCount,
                    mData.length - positionStart - itemCount);
            Arrays.fill(mData, positionStart, positionStart + itemCount,
                    LayoutParams.INVALID_SPAN_ID);
            offsetFullSpansForAddition(positionStart, itemCount);
        }

        private void offsetFullSpansForAddition(int positionStart, int itemCount) {
            if (mAlignSpanItems == null) {
                return;
            }
            for (int i = mAlignSpanItems.size() - 1; i >= 0; i--) {
                AlignSpanItem fsi = mAlignSpanItems.get(i);
                if (fsi.mPosition < positionStart) {
                    continue;
                }
                fsi.mPosition += itemCount;
            }
        }

        /**
         * Returns when invalidation should end. e.g. hitting a full span position.
         * Returned position SHOULD BE invalidated.
         */
        private int invalidateFullSpansAfter(int position) {
            if (mAlignSpanItems == null) {
                return RecyclerView.NO_POSITION;
            }
            final AlignSpanItem item = getFullSpanItem(position);
            // if there is an fsi at this position, get rid of it.
            if (item != null) {
                mAlignSpanItems.remove(item);
            }
            int nextFsiIndex = -1;
            final int count = mAlignSpanItems.size();
            for (int i = 0; i < count; i++) {
                AlignSpanItem fsi = mAlignSpanItems.get(i);
                if (fsi.mPosition >= position) {
                    nextFsiIndex = i;
                    break;
                }
            }
            if (nextFsiIndex != -1) {
                AlignSpanItem fsi = mAlignSpanItems.get(nextFsiIndex);
                mAlignSpanItems.remove(nextFsiIndex);
                return fsi.mPosition;
            }
            return RecyclerView.NO_POSITION;
        }

        public void addAlignSpanItem(AlignSpanItem alignSpanItem) {
            if (mAlignSpanItems == null) {
                mAlignSpanItems = new ArrayList<>();
            }
            final int size = mAlignSpanItems.size();
            for (int i = 0; i < size; i++) {
                AlignSpanItem other = mAlignSpanItems.get(i);
                if (other.mPosition == alignSpanItem.mPosition) {
                    if (DEBUG) {
                        throw new IllegalStateException("two fsis for same position");
                    }
                    mAlignSpanItems.set(i, alignSpanItem);
                    return;
                } else if (other.mPosition > alignSpanItem.mPosition) {
                    mAlignSpanItems.add(i, alignSpanItem);
                    return;
                }
            }
            // if it is not added to a position.
            mAlignSpanItems.add(alignSpanItem);
        }

        public AlignSpanItem getFullSpanItem(int position) {
            if (mAlignSpanItems == null) {
                return null;
            }
            for (int i = mAlignSpanItems.size() - 1; i >= 0; i--) {
                final AlignSpanItem fsi = mAlignSpanItems.get(i);
                if (fsi.mPosition == position) {
                    return fsi;
                }
            }
            return null;
        }

        /**
         * @param minPos              inclusive
         * @param maxPos              exclusive
         * @param gapDir              if not 0, returns FSIs on in that direction
         * @param hasUnwantedGapAfter If true, when full span item has unwanted gaps, it will be
         *                            returned even if its gap direction does not match.
         */
        public AlignSpanItem getFirstFullSpanItemInRange(int minPos, int maxPos, int gapDir,
                                                         boolean hasUnwantedGapAfter) {
            if (mAlignSpanItems == null) {
                return null;
            }
            final int limit = mAlignSpanItems.size();
            for (int i = 0; i < limit; i++) {
                AlignSpanItem fsi = mAlignSpanItems.get(i);
                if (fsi.mPosition >= maxPos) {
                    return null;
                }
                if (fsi.mPosition >= minPos
                        && (gapDir == 0 || fsi.mGapDir == gapDir
                        || (hasUnwantedGapAfter && fsi.mHasUnwantedGapAfter))) {
                    return fsi;
                }
            }
            return null;
        }

        /**
         * We keep information about full span items because they may create gaps in the UI.
         */
        @SuppressLint("BanParcelableUsage")
        static class AlignSpanItem implements Parcelable {

            int mPosition;
            int mGapDir;
            int[] mGapPerSpan;
            // A full span may be laid out in primary direction but may have gaps due to
            // invalidation of views after it. This is recorded during a reverse scroll and if
            // view is still on the screen after scroll stops, we have to recalculate layout
            boolean mHasUnwantedGapAfter;

            AlignSpanItem(Parcel in) {
                mPosition = in.readInt();
                mGapDir = in.readInt();
                mHasUnwantedGapAfter = in.readInt() == 1;
                int spanCount = in.readInt();
                if (spanCount > 0) {
                    mGapPerSpan = new int[spanCount];
                    in.readIntArray(mGapPerSpan);
                }
            }

            AlignSpanItem() {
            }

            int getGapForSpan(int spanIndex) {
                return mGapPerSpan == null ? 0 : mGapPerSpan[spanIndex];
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                dest.writeInt(mPosition);
                dest.writeInt(mGapDir);
                dest.writeInt(mHasUnwantedGapAfter ? 1 : 0);
                if (mGapPerSpan != null && mGapPerSpan.length > 0) {
                    dest.writeInt(mGapPerSpan.length);
                    dest.writeIntArray(mGapPerSpan);
                } else {
                    dest.writeInt(0);
                }
            }

            @Override
            public String toString() {
                return "FullSpanItem{"
                        + "mPosition=" + mPosition
                        + ", mGapDir=" + mGapDir
                        + ", mHasUnwantedGapAfter=" + mHasUnwantedGapAfter
                        + ", mGapPerSpan=" + Arrays.toString(mGapPerSpan)
                        + '}';
            }

            public static final Parcelable.Creator<AlignSpanItem> CREATOR =
                    new Parcelable.Creator<AlignSpanItem>() {
                        @Override
                        public AlignSpanItem createFromParcel(Parcel in) {
                            return new AlignSpanItem(in);
                        }

                        @Override
                        public AlignSpanItem[] newArray(int size) {
                            return new AlignSpanItem[size];
                        }
                    };
        }
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY)
    @SuppressLint("BanParcelableUsage")
    public static class SavedState implements Parcelable {

        int mAnchorPosition;
        int mVisibleAnchorPosition; // Replacement for span info when spans are invalidated
        int mSpanOffsetsSize;
        int[] mSpanOffsets;
        int mSpanLookupSize;
        int[] mSpanLookup;
        List<LazySpanLookup.AlignSpanItem> mAlignSpanItems;
        boolean mReverseLayout;
        boolean mAnchorLayoutFromEnd;
        boolean mLastLayoutRTL;

        public SavedState() {
        }

        SavedState(Parcel in) {
            mAnchorPosition = in.readInt();
            mVisibleAnchorPosition = in.readInt();
            mSpanOffsetsSize = in.readInt();
            if (mSpanOffsetsSize > 0) {
                mSpanOffsets = new int[mSpanOffsetsSize];
                in.readIntArray(mSpanOffsets);
            }

            mSpanLookupSize = in.readInt();
            if (mSpanLookupSize > 0) {
                mSpanLookup = new int[mSpanLookupSize];
                in.readIntArray(mSpanLookup);
            }
            mReverseLayout = in.readInt() == 1;
            mAnchorLayoutFromEnd = in.readInt() == 1;
            mLastLayoutRTL = in.readInt() == 1;
            @SuppressWarnings("unchecked")
            List<LazySpanLookup.AlignSpanItem> alignSpanItems =
                    in.readArrayList(LazySpanLookup.AlignSpanItem.class.getClassLoader());
            mAlignSpanItems = alignSpanItems;
        }

        public SavedState(SavedState other) {
            mSpanOffsetsSize = other.mSpanOffsetsSize;
            mAnchorPosition = other.mAnchorPosition;
            mVisibleAnchorPosition = other.mVisibleAnchorPosition;
            mSpanOffsets = other.mSpanOffsets;
            mSpanLookupSize = other.mSpanLookupSize;
            mSpanLookup = other.mSpanLookup;
            mReverseLayout = other.mReverseLayout;
            mAnchorLayoutFromEnd = other.mAnchorLayoutFromEnd;
            mLastLayoutRTL = other.mLastLayoutRTL;
            mAlignSpanItems = other.mAlignSpanItems;
        }

        void invalidateSpanInfo() {
            mSpanOffsets = null;
            mSpanOffsetsSize = 0;
            mSpanLookupSize = 0;
            mSpanLookup = null;
            mAlignSpanItems = null;
        }

        void invalidateAnchorPositionInfo() {
            mSpanOffsets = null;
            mSpanOffsetsSize = 0;
            mAnchorPosition = RecyclerView.NO_POSITION;
            mVisibleAnchorPosition = RecyclerView.NO_POSITION;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mAnchorPosition);
            dest.writeInt(mVisibleAnchorPosition);
            dest.writeInt(mSpanOffsetsSize);
            if (mSpanOffsetsSize > 0) {
                dest.writeIntArray(mSpanOffsets);
            }
            dest.writeInt(mSpanLookupSize);
            if (mSpanLookupSize > 0) {
                dest.writeIntArray(mSpanLookup);
            }
            dest.writeInt(mReverseLayout ? 1 : 0);
            dest.writeInt(mAnchorLayoutFromEnd ? 1 : 0);
            dest.writeInt(mLastLayoutRTL ? 1 : 0);
            dest.writeList(mAlignSpanItems);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    /**
     * Data class to hold the information about an anchor position which is used in onLayout call.
     */
    class AnchorInfo {

        int mPosition;
        int mOffset;
        boolean mLayoutFromEnd;
        boolean mInvalidateOffsets;
        boolean mValid;
        // this is where we save span reference lines in case we need to re-use them for multi-pass
        // measure steps
        int[] mSpanReferenceLines;

        AnchorInfo() {
            reset();
        }

        void reset() {
            mPosition = RecyclerView.NO_POSITION;
            mOffset = INVALID_OFFSET;
            mLayoutFromEnd = false;
            mInvalidateOffsets = false;
            mValid = false;
            if (mSpanReferenceLines != null) {
                Arrays.fill(mSpanReferenceLines, -1);
            }
        }

        void saveSpanReferenceLines(Span[] spans) {
            int spanCount = spans.length;
            if (mSpanReferenceLines == null || mSpanReferenceLines.length < spanCount) {
                mSpanReferenceLines = new int[mSpans.length];
            }
            for (int i = 0; i < spanCount; i++) {
                // does not matter start or end since this is only recorded when span is reset
                mSpanReferenceLines[i] = spans[i].getStartLine(Span.INVALID_LINE);
            }
        }

        void assignCoordinateFromPadding() {
            mOffset = mLayoutFromEnd ? mPrimaryOrientation.getEndAfterPadding()
                    : mPrimaryOrientation.getStartAfterPadding();
        }

        void assignCoordinateFromPadding(int addedDistance) {
            if (mLayoutFromEnd) {
                mOffset = mPrimaryOrientation.getEndAfterPadding() - addedDistance;
            } else {
                mOffset = mPrimaryOrientation.getStartAfterPadding() + addedDistance;
            }
        }
    }

    private static class Brother {
        int start;
        int end;
        int otherStart;
        int otherEnd;

        Span currentSpan;

        static Brother get(View view, LayoutParams lp, int position) {
            return new Brother(view, lp, position);
        }

        final View view;
        final LayoutParams lp;
        final int position;

        Brother(View view, LayoutParams lp, int position) {
            this.view = view;
            this.lp = lp;
            this.position = position;
        }
    }

    public interface SpanSizeLookup {
        boolean isStaggeredStyle(int position);

        int getStaggeredSpanSize();

        int getGridSpanSize(int position);
    }
}
