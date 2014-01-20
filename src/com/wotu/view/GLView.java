package com.wotu.view;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.wotu.anim.AnimTimer;
import com.wotu.anim.CanvasAnim;
import com.wotu.common.WLog;
import com.wotu.utils.UtilsBase;
import com.wotu.view.opengl.GLCanvas;
import com.wotu.view.opengl.GLRoot;

import java.util.ArrayList;

@SuppressLint("WrongCall")
public class GLView {

    private static final String TAG = "GLView";
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 1;

    private static final int FLAG_INVISIBLE = 1;
    private static final int FLAG_SET_MEASURED_SIZE = 2;
    private static final int FLAG_LAYOUT_REQUESTED = 4;

    private GLRoot mRoot;
    protected GLView mParent;
    private GLView mMotionTarget;
    private ArrayList<GLView> mChilds;
    private CanvasAnim mAnimation;

    private int mViewFlags = 0;

    protected int mMeasuredWidth = 0;
    protected int mMeasuredHeight = 0;

    private int mLastWidthSpec = -1;
    private int mLastHeightSpec = -1;

    protected final Rect mBounds = new Rect();
    protected final Rect mPaddings = new Rect();

    protected int mScrollX = 0;
    protected int mScrollY = 0;

    // 渲染
    protected void render(GLCanvas canvas) {
        renderBackground(canvas);
        for (int i = 0, n = getChildCount(); i < n; ++i) {
            renderChild(canvas, getChild(i));
        }
    }

    protected void renderBackground(GLCanvas view) {
    }

    protected void renderChild(GLCanvas canvas, GLView component) {
        if (component.getVisibility() != GLView.VISIBLE
                && component.mAnimation == null)
            return;

        int xoffset = component.mBounds.left - mScrollX;
        int yoffset = component.mBounds.top - mScrollY;

        canvas.translate(xoffset, yoffset);

        CanvasAnim anim = component.mAnimation;
        if (anim != null) {
            canvas.save(anim.getCanvasSaveFlags());
            if (anim.calculate(AnimTimer.get())) {
                invalidate();
            } else {
                component.mAnimation = null;
            }
            anim.apply(canvas);
        }
        component.render(canvas);
        if (anim != null)
            canvas.restore();
        canvas.translate(-xoffset, -yoffset);
    }

    public GLRoot getGLRoot() {
        return mRoot;
    }

    // Request re-rendering of the view hierarchy.
    // This is used for animation or when the contents changed.
    public void invalidate() {
        GLRoot root = getGLRoot();
        if (root != null)
            root.requestRender();
    }

    public void startAnimation(CanvasAnim animation) {
        GLRoot root = getGLRoot();
        if (root == null)
            throw new IllegalStateException();
        mAnimation = animation;
        if (mAnimation != null) {
            mAnimation.start();
            root.registerLaunchedAnimation(mAnimation);
        }
        invalidate();
    }

    // Sets the visiblity of this GLView (either GLView.VISIBLE or
    // GLView.INVISIBLE).
    public void setVisibility(int visibility) {
        if (visibility == getVisibility())
            return;
        if (visibility == VISIBLE) {
            mViewFlags &= ~FLAG_INVISIBLE;
        } else {
            mViewFlags |= FLAG_INVISIBLE;
        }
        onVisibilityChanged(visibility);
        invalidate();
    }

    protected void onVisibilityChanged(int visibility) {
        for (int i = 0, n = getChildCount(); i < n; ++i) {
            GLView child = getChild(i);
            if (child.getVisibility() == GLView.VISIBLE) {
                child.onVisibilityChanged(visibility);
            }
        }
    }

    // Returns GLView.VISIBLE or GLView.INVISIBLE
    public int getVisibility() {
        return (mViewFlags & FLAG_INVISIBLE) == 0 ? VISIBLE : INVISIBLE;
    }

    public Rect getPaddings() {
        return mPaddings;
    }

    // layout handing
    public void layout(int left, int top, int right, int bottom) {
        boolean sizeChanged = setBounds(left, top, right, bottom);
        mViewFlags &= ~FLAG_LAYOUT_REQUESTED;
        // We call onLayout no matter sizeChanged is true or not because the
        // orientation may change without changing the size of the View (for
        // example, rotate the device by 180 degrees), and we want to handle
        // orientation change in onLayout.
        onLayout(sizeChanged, left, top, right, bottom);
    }

    protected void onLayout( // 子类可以重写
            boolean changeSize, int left, int top, int right, int bottom) {
    }

    private boolean setBounds(int left, int top, int right, int bottom) {
        boolean sizeChanged = (right - left) != (mBounds.right - mBounds.left)
                || (bottom - top) != (mBounds.bottom - mBounds.top);
        mBounds.set(left, top, right, bottom);
        return sizeChanged;
    }

    // Request re-layout of the view hierarchy.
    public void requestLayout() {
        mViewFlags |= FLAG_LAYOUT_REQUESTED;
        mLastHeightSpec = -1;
        mLastWidthSpec = -1;
        if (mParent != null) {
            mParent.requestLayout();
        } else {
            // Is this a content pane ?
            GLRoot root = getGLRoot();
            if (root != null)
                root.requestLayoutContentPane();
        }
    }

    /**
     * Gets the bounds of the given descendant that relative to this view.
     */
    public boolean getBoundsOf(GLView descendant, Rect out) {
        int xoffset = 0;
        int yoffset = 0;
        GLView view = descendant;
        while (view != this) {
            if (view == null)
                return false;
            Rect bounds = view.mBounds;
            xoffset += bounds.left;
            yoffset += bounds.top;
            view = view.mParent;
        }
        out.set(xoffset, yoffset, xoffset + descendant.getWidth(),
                yoffset + descendant.getHeight());
        return true;
    }

    // measure handing
    public void measure(int widthSpec, int heightSpec) {
        if (widthSpec == mLastWidthSpec && heightSpec == mLastHeightSpec
                && (mViewFlags & FLAG_LAYOUT_REQUESTED) == 0) {
            return;
        }

        mLastWidthSpec = widthSpec;
        mLastHeightSpec = heightSpec;

        mViewFlags &= ~FLAG_SET_MEASURED_SIZE;
        onMeasure(widthSpec, heightSpec);
        if ((mViewFlags & FLAG_SET_MEASURED_SIZE) == 0) {
            throw new IllegalStateException(getClass().getName()
                    + " should call setMeasuredSize() in onMeasure()");
        }
    }

    protected void onMeasure(int widthSpec, int heightSpec) {
    }

    protected void setMeasuredSize(int width, int height) {
        mViewFlags |= FLAG_SET_MEASURED_SIZE;
        mMeasuredWidth = width;
        mMeasuredHeight = height;
    }

    public int getMeasuredWidth() {
        return mMeasuredWidth;
    }

    public int getMeasuredHeight() {
        return mMeasuredHeight;
    }

    //事件分发
    protected boolean dispatchTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        int action = event.getAction();
        if (mMotionTarget != null) {
            if (action == MotionEvent.ACTION_DOWN) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                dispatchTouchEvent(cancel, x, y, mMotionTarget, false);
                mMotionTarget = null;
            } else {
                dispatchTouchEvent(event, x, y, mMotionTarget, false);
                if (action == MotionEvent.ACTION_CANCEL
                        || action == MotionEvent.ACTION_UP) {
                    mMotionTarget = null;
                }
                return true;
            }
        }
        if (action == MotionEvent.ACTION_DOWN) {
            // in the reverse rendering order
            for (int i = getChildCount() - 1; i >= 0; --i) {
                GLView component = getChild(i);
                if (component.getVisibility() != GLView.VISIBLE)
                    continue;
                if (dispatchTouchEvent(event, x, y, component, true)) {
                    mMotionTarget = component;
                    return true;
                }
            }
        }
        return onTouch(event);
    }

    protected boolean onTouch(MotionEvent event) {
        return false;
    }

    protected boolean dispatchTouchEvent(MotionEvent event,
            int x, int y, GLView component, boolean checkBounds) {
        Rect rect = component.mBounds;
        int left = rect.left;
        int top = rect.top;
        if (!checkBounds || rect.contains(x, y)) {
            event.offsetLocation(-left, -top);
            if (component.dispatchTouchEvent(event)) {
                event.offsetLocation(left, top);
                return true;
            }
            event.offsetLocation(left, top);
        }
        return false;
    }

    public Rect bounds() {
        return mBounds;
    }

    public int getWidth() {
        return mBounds.right - mBounds.left;
    }

    public int getHeight() {
        return mBounds.bottom - mBounds.top;
    }

    // This should only be called on the content pane (the topmost GLView).
    public void attachToRoot(GLRoot root) {
        UtilsBase.assertTrue(mParent == null && mRoot == null); //必须是根GLview才可以从外部调用attach
        onAttachToRoot(root);
    }

    // This should only be called on the content pane (the topmost GLView).
    public void detachFromRoot() {
        UtilsBase.assertTrue(mParent == null && mRoot != null); //必须是根GLview才可以从外部调用detach
        onDetachFromRoot();
    }

    protected void onAttachToRoot(GLRoot root) {
        mRoot = root;
        for (int i = 0, n = getChildCount(); i < n; ++i) {
            getChild(i).onAttachToRoot(root); //子GLview的attach办法
        }
    }

    protected void onDetachFromRoot() {
        for (int i = 0, n = getChildCount(); i < n; ++i) {
            getChild(i).onDetachFromRoot(); ////子GLview的detach办法
        }
        mRoot = null;
    }

    //----------------------------component operations-----------------------------
    // Returns the number of children of the GLView.
    public int getChildCount() {
        return mChilds == null ? 0 : mChilds.size();
    }

    // Returns the children for the given index.
    public GLView getChild(int index) {
        if (mChilds == null) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mChilds.get(index);
    }

    // Adds a child to this GLView.
    public void addChild(GLView component) {
        // Make sure the component doesn't have a parent currently.
        if (component.mParent != null)
            throw new IllegalStateException();

        // Build parent-child links
        if (mChilds == null) {
            mChilds = new ArrayList<GLView>();
        }
        mChilds.add(component);
        component.mParent = this;

        // If this is added after we have a root, tell the component.
        if (mRoot != null) {
            component.onAttachToRoot(mRoot);
        }
    }

    // Removes a child from this GLView.
    public boolean removeChild(GLView component) {
        if (mChilds == null)
            return false;
        if (mChilds.remove(component)) {
            removeOneChild(component);
            return true;
        }
        return false;
    }

    // Removes all children of this GLView.
    public void removeAllChilds() {
        for (int i = 0, n = mChilds.size(); i < n; ++i) {
            removeOneChild(mChilds.get(i));
        }
        mChilds.clear();
    }

    private void removeOneChild(GLView component) {
        if (mMotionTarget == component) {
            long now = SystemClock.uptimeMillis();
            MotionEvent cancelEvent = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            dispatchTouchEvent(cancelEvent);
            cancelEvent.recycle();
        }
        component.onDetachFromRoot();
        component.mParent = null;
    }

    //----------------------------end component operations-----------------------------

    public void lockRendering() {
        if (mRoot != null) {
            mRoot.lockRenderThread();
        }
    }

    public void unlockRendering() {
        if (mRoot != null) {
            mRoot.unlockRenderThread();
        }
    }

    // This is for debugging only.
    // Dump the view hierarchy into log.
    void dumpTree(String prefix) {
        WLog.d(TAG, prefix + getClass().getSimpleName());
        for (int i = 0, n = getChildCount(); i < n; ++i) {
            getChild(i).dumpTree(prefix + "....");
        }
    }
}
