# MixedGridLayoutManager

这是一个同时支持瀑布流和水平网格的布局管理器, 它基于官方androidx.recyclerview:recyclerview:1.1.0的StaggeredGridLayoutManager进行二次开发, 使用上类似于GridLayoutManager.

## 如何使用

1. 类似`GridLayoutManager`, 需设置一个回调, 用来判断并获取一个item所占据的`spanSize`:

```java
    public void setSpanSizeLookup(SpanSizeLookup spanSizeLookup) {
        this.mSpanSizeLookup = spanSizeLookup;
    }
```

2. 方法`isStaggeredStyle`用于判断当前item是否属于瀑布流样式. 当返回true时表示是瀑布流, 瀑布流样式的item其`spanSize`必须一样, 其取值通过方法`getStaggeredSpanSize`返回. 当返回false时表示是填充对齐的网格样式, 此时通过`getGridSpanSize`返回对应的`spanSize`. 另外, 当`getGridSpanSize`返回的值等于该布局管理器的`spanCount`时, 该item表示是`isFullSpan`, 就使用而言这不必过多关注, 它是改造遗留的痕迹之一.

```java
public interface SpanSizeLookup {
    boolean isStaggeredStyle(int position);
    int getStaggeredSpanSize();
    int getGridSpanSize(int position);
}
```

3. 走进回调接口的使用处, 它返回的值主要是赋给`LayoutParams#mSpanSize`, 并通过正负值区分一个item的布局样式是瀑布流还是网格. 知其然知其所以然, 更多的分析留在后面.

```java
    private int getSpanSize(int position) {
        final boolean ss = mSpanSizeLookup.isStaggeredStyle(position);
        if (ss) {
            return -Math.abs(mSpanSizeLookup.getStaggeredSpanSize());
        } else {
            return Math.abs(mSpanSizeLookup.getGridSpanSize(position));
        }
    }
```

## 瀑布流布局管理器源码简析

> [请移步]()

## 布局管理器改造过程

## 一些坑

## 总结


