package com.wotu.view.opengl;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import java.util.ArrayList;

import com.wotu.common.WLog;

// This class is similar to BitmapTexture, except the bitmap is
// split into tiles. By doing so, we may increase the time required to
// upload the whole bitmap but we reduce the time of uploading each tile
// so it make the animation more smooth and prevents jank.
public class TiledTexture implements Texture {

    private static final String TAG = "TiledTexture";

    private static final int CONTENT_SIZE = 254;
    private static final int BORDER_SIZE = 1;
    private static final int TILE_SIZE = CONTENT_SIZE + 2 * BORDER_SIZE;

    private static Tile sFreeTileHead = null;
    private static final Object sFreeTileLock = new Object();

    private static Bitmap sUploadBitmap;
    private static Canvas sCanvas;
    private static Paint sBitmapPaint;
    private static Paint sPaint;

    private int mUploadIndex = 0;

    private final Tile[] mTiles; // Can be modified in different threads. Should be protected by "synchronized."
    private final int mWidth;
    private final int mHeight;
    private final RectF mSrcRect = new RectF();
    private final RectF mDestRect = new RectF();

    private static class Tile extends UploadedTexture {
        public int offsetX;
        public int offsetY;
        public Bitmap bitmap;
        public Tile nextFreeTile;
        public int contentWidth;
        public int contentHeight;

        @Override
        public void setSize(int width, int height) {
            contentWidth = width;
            contentHeight = height;
            mWidth = width + 2 * BORDER_SIZE;
            mHeight = height + 2 * BORDER_SIZE;
            mTextureWidth = TILE_SIZE;
            mTextureHeight = TILE_SIZE;
        }

        @Override
        protected Bitmap onGetBitmap() {
            // make a local copy of the reference to the bitmap,
            // since it might be null'd in a different thread. b/8694871
            Bitmap localBitmapRef = bitmap;
            bitmap = null;

            if (localBitmapRef != null) {
                int x = BORDER_SIZE - offsetX;
                int y = BORDER_SIZE - offsetY;
                int r = localBitmapRef.getWidth() + x;
                int b = localBitmapRef.getHeight() + y;
                sCanvas.drawBitmap(localBitmapRef, x, y, sBitmapPaint);
                localBitmapRef = null;

                // draw borders if need
                if (x > 0)
                    sCanvas.drawLine(x - 1, 0, x - 1, TILE_SIZE, sPaint);
                if (y > 0)
                    sCanvas.drawLine(0, y - 1, TILE_SIZE, y - 1, sPaint);
                if (r < CONTENT_SIZE)
                    sCanvas.drawLine(r, 0, r, TILE_SIZE, sPaint);
                if (b < CONTENT_SIZE)
                    sCanvas.drawLine(0, b, TILE_SIZE, b, sPaint);
            }

            return sUploadBitmap;
        }

        @Override
        protected void onFreeBitmap(Bitmap bitmap) {
            // do nothing
        }
    }

    private static void freeTile(Tile tile) {
        tile.invalidateContent();
        tile.bitmap = null;
        synchronized (sFreeTileLock) {
            tile.nextFreeTile = sFreeTileHead;
            sFreeTileHead = tile;
        }
    }

    private static Tile obtainTile() {
        synchronized (sFreeTileLock) {
            Tile result = sFreeTileHead;
            if (result == null)
                return new Tile();
            sFreeTileHead = result.nextFreeTile;
            result.nextFreeTile = null;
            return result;
        }
    }

    public boolean uploadNextTile(GLCanvas canvas) {
        if (mUploadIndex == mTiles.length)
            return true;

        synchronized (mTiles) {
            Tile next = mTiles[mUploadIndex++];

            // Make sure tile has not already been recycled by the time
            // this is called (race condition in onGLIdle)
            if (next.bitmap != null) {
                boolean hasBeenLoad = next.isLoaded();
                next.updateContent(canvas);

                // It will take some time for a texture to be drawn for the first
                // time. When scrolling, we need to draw several tiles on the screen
                // at the same time. It may cause a UI jank even these textures has
                // been uploaded.
                if (!hasBeenLoad)
                    next.draw(canvas, 100, 100);
            }
        }
        return mUploadIndex == mTiles.length;
    }

    public TiledTexture(Bitmap bitmap) {
        mWidth = bitmap.getWidth();
        mHeight = bitmap.getHeight();
        ArrayList<Tile> list = new ArrayList<Tile>();

        for (int x = 0, w = mWidth; x < w; x += CONTENT_SIZE) {
            for (int y = 0, h = mHeight; y < h; y += CONTENT_SIZE) {
                Tile tile = obtainTile();
                tile.offsetX = x;
                tile.offsetY = y;
                tile.bitmap = bitmap;
                tile.setSize(Math.min(CONTENT_SIZE, mWidth - x), Math.min(CONTENT_SIZE, mHeight - y));
                list.add(tile);
            }
        }
        mTiles = list.toArray(new Tile[list.size()]);
    }

    public boolean isReady() {
        return mUploadIndex == mTiles.length;
    }

    // Can be called in UI thread.
    public void recycle() {
        synchronized (mTiles) {
            for (int i = 0, n = mTiles.length; i < n; ++i) {
                freeTile(mTiles[i]);
            }
        }
    }

    public static void freeResources() {
        sUploadBitmap = null;
        sCanvas = null;
        sBitmapPaint = null;
        sPaint = null;
    }

    public static void prepareResources() {
        sUploadBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Config.ARGB_8888);
        sCanvas = new Canvas(sUploadBitmap);
        sBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        sBitmapPaint.setXfermode(new PorterDuffXfermode(Mode.SRC));
        sPaint = new Paint();
        sPaint.setXfermode(new PorterDuffXfermode(Mode.SRC));
        sPaint.setColor(Color.TRANSPARENT);
    }

    // We want to draw the "source" on the "target".
    // This method is to find the "output" rectangle which is
    // the corresponding area of the "src".
    //                                   (x,y)  target
    // (x0,y0)  source                     +---------------+
    //    +----------+                     |               |
    //    | src      |                     | output        |
    //    | +--+     |    linear map       | +----+        |
    //    | +--+     |    ---------->      | |    |        |
    //    |          | by (scaleX, scaleY) | +----+        |
    //    +----------+                     |               |
    //      Texture                        +---------------+
    //                                          Canvas
    private static void mapRect(RectF output,
            RectF src, float x0, float y0, float x, float y, float scaleX,
            float scaleY) {
        output.set(x + (src.left - x0) * scaleX,
                y + (src.top - y0) * scaleY,
                x + (src.right - x0) * scaleX,
                y + (src.bottom - y0) * scaleY);
    }

    // Draws a mixed color of this texture and a specified color onto the
    // a rectangle. The used color is: from * (1 - ratio) + to * ratio.
    public void drawMixed(GLCanvas canvas, int color, float ratio, int x, int y, int width, int height) {
        RectF src = mSrcRect;
        RectF dest = mDestRect;
        float scaleX = (float) width / mWidth;
        float scaleY = (float) height / mHeight;
        synchronized (mTiles) {
            for (int i = 0, n = mTiles.length; i < n; ++i) {
                Tile t = mTiles[i];
                src.set(0, 0, t.contentWidth, t.contentHeight);
                src.offset(t.offsetX, t.offsetY);
                mapRect(dest, src, 0, 0, x, y, scaleX, scaleY);
                src.offset(BORDER_SIZE - t.offsetX, BORDER_SIZE - t.offsetY);
                canvas.drawMixed(t, color, ratio, mSrcRect, mDestRect);
            }
        }
    }

    // Draws the texture on to the specified rectangle.
    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        RectF src = mSrcRect;
        RectF dest = mDestRect;
        float scaleX = (float) width / mWidth;
        float scaleY = (float) height / mHeight;
        synchronized (mTiles) {
            for (int i = 0, n = mTiles.length; i < n; ++i) {
                Tile t = mTiles[i];
                src.set(0, 0, t.contentWidth, t.contentHeight);
                src.offset(t.offsetX, t.offsetY);
                mapRect(dest, src, 0, 0, x, y, scaleX, scaleY);
                src.offset(BORDER_SIZE - t.offsetX, BORDER_SIZE - t.offsetY);
                canvas.drawTexture(t, mSrcRect, mDestRect);
            }
        }
    }

    // Draws a sub region of this texture on to the specified rectangle.
    public void draw(GLCanvas canvas, RectF source, RectF target) {
        RectF src = mSrcRect;
        RectF dest = mDestRect;
        float x0 = source.left;
        float y0 = source.top;
        float x = target.left;
        float y = target.top;
        float scaleX = target.width() / source.width();
        float scaleY = target.height() / source.height();

        synchronized (mTiles) {
            for (int i = 0, n = mTiles.length; i < n; ++i) {
                Tile t = mTiles[i];
                src.set(0, 0, t.contentWidth, t.contentHeight);
                src.offset(t.offsetX, t.offsetY);
                if (!src.intersect(source))
                    continue;
                mapRect(dest, src, x0, y0, x, y, scaleX, scaleY);
                src.offset(BORDER_SIZE - t.offsetX, BORDER_SIZE - t.offsetY);
                canvas.drawTexture(t, src, dest);
            }
        }
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y) {
        draw(canvas, x, y, mWidth, mHeight);
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }
}
