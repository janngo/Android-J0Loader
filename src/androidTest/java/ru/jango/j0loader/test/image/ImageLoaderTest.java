package ru.jango.j0loader.test.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;

import ru.jango.j0loader.Request;
import ru.jango.j0loader.image.ImageLoader;
import ru.jango.j0loader.image.cache.LRUCache;
import ru.jango.j0loader.test.Settings;
import ru.jango.j0loader.test.LoadingAdapter2;
import ru.jango.j0util.LogUtil;

public class ImageLoaderTest extends AndroidTestCase {

    /**
     * Test scale resolving algorithm.
     */
    public void testScaleLarger() throws Exception {
        final ImageLoaderWrapper loader = new ImageLoaderWrapper();

        assertFalse(loader.scaleLarger2(null, null));
        assertFalse(loader.scaleLarger2(null, new Point(100, 100)));
        assertTrue(loader.scaleLarger2(new Point(100, 100), null));
        assertFalse(loader.scaleLarger2(new Point(100, 100), new Point(200, 200)));
        assertFalse(loader.scaleLarger2(new Point(10, 10), new Point(11, 11)));
        assertFalse(loader.scaleLarger2(new Point(11, 11), new Point(10, 10)));
        assertTrue(loader.scaleLarger2(new Point(15, 15), new Point(10, 10)));
    }

    /**
     * Test adding into cache and simple queues.
     * 1) create loader and requests
     * 2) add same request two times - should be added into simple queue once
     * 3) add fake item into cache and small scale into scales
     * 4) add request with small scale for that fake item and check it was added into cache queue
     * 5) add request with larger scale and check cache is empty and request was added into simple queue
     */
    public void testAddToQueue() throws Exception {
        // 1
        final ImageLoaderWrapper loader = new ImageLoaderWrapper();
        final Request r1 = new Request(Settings.IMG_SMALL);
        final Request r2 = new Request(Settings.IMG_NORMAL);

        // 2
        loader.addToQueue(r1);
        loader.addToQueue(r1);
        assertEquals(1, loader.getQueueSize());
        assertEquals(0, loader.getCacheQueueSize());

        // 3
        loader.getCache().put(r2.getURI(), new byte[8]);
        loader.getCache().setScale(r2.getURI(), new Point(10, 10));

        // 4
        loader.addToQueue(r2, new Point(11, 11));
        assertEquals(1, loader.getQueueSize());
        assertEquals(1, loader.getCacheQueueSize());
        assertEquals(1, loader.getCache().scalesCount());

        // 5
        loader.addToQueue(r2, new Point(100, 100));
        assertEquals(2, loader.getQueueSize());
        assertEquals(0, loader.getCacheQueueSize());
        assertEquals(0, loader.getCache().count());
        assertEquals(1, loader.getCache().scalesCount());
    }

    /**
     * Test loading from file - it should work just the same way as HTTP downloading, since URI and
     * URLConnection both can work file file:// schema.
     * 1) prepare bitmap and paths
     * 2) save bitmap from resources in internal storage
     * 3) "download" saved bitmap
     * 4) check content size in Request object
     * 5) check bitmap itself
     */
    public void testFileDownload() throws Exception {
        // 1
        //noinspection ConstantConditions
        final Bitmap small = BitmapFactory.decodeResource(getContext().getResources(), ru.jango.j0loader.test.R.drawable.small);
        final File path = new File(getContext().getFilesDir(), "small.jpg");
        final URI smallUri = new URI("file://" + path.getAbsolutePath());

        // 2
        final FileOutputStream fos = new FileOutputStream(path);
        small.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.close();

        // 3
        final ImageLoaderWrapper loader = new ImageLoaderWrapper(new LoadingAdapter2<Bitmap>() {
            @Override
            public void processFinished(Request request, byte[] rawData, Bitmap data) {
                super.processFinished(request, rawData, data);

                // 4
                LogUtil.d(ImageLoaderTest.class, "file content length = " + request.getResponseContentLength());
                assertTrue(request.getResponseContentLength() != -1);

                // 5
                assertEquals(850, data.getWidth());
                assertEquals(1108, data.getHeight());
                assertEquals(489061, rawData.length);
            }
        });
        loader.addToQueue(new Request(smallUri));
        loader.start();

        waitLoadingThreads(loader);
    }

    /**
     * Simple downloading without scaling.
     * 1) create a loader
     * 2) init queue with all images from Settings
     * 3) check loaded images dimensions and sizes
     * 4) check failed image (Settings.FAKE) (by URI)
     */
    public void testSimpleDownload() throws Exception {
        // 1
        final int[][] assertValues = {{425, 554, 80492}, {1600, 1000, 483849},
                {1999, 1095, 1978649}, {2000, 812, 1688679}};
        final ImageLoaderWrapper loader = new ImageLoaderWrapper(new AssertListener(assertValues));

        // 2
        for (Settings.Img img : Settings.Img.values())
            loader.addToQueue(new Request(img.getURI()));
        loader.start();

        // 3-4 - auto in AssertListener
        waitLoadingThreads(loader);
    }

    /**
     * Loading images with scaling.
     * 1) create a loader
     * 2) init queue with all images from Settings (some should be added several times and with scales)
     * 3) check loaded images dimensions and sizes
     * 4) check failed image (Settings.FAKE) (by URI)
     */
    public void testScalingDownload() throws Exception {
        // 1
        final int[][] assertValues = {{153, 200, 45016}, {1600, 1000, 483849},
                {399, 218, 136436}, {2000, 812, 1688679}};
        final ImageLoaderWrapper loader = new ImageLoaderWrapper(new AssertListener(assertValues));

        // 2
        loader.addToQueue(new Request(Settings.IMG_SMALL)); // would be ignored
        loader.addToQueue(new Request(Settings.IMG_SMALL), new Point(200, 200)); // would be loaded and scaled
        loader.addToQueue(new Request(Settings.IMG_SMALL)); // ignored
        loader.addToQueue(new Request(Settings.IMG_NORMAL)); // loaded
        loader.addToQueue(new Request(Settings.IMG_LARGE), new Point(400, 400)); // loaded and scaled
        loader.addToQueue(new Request(Settings.IMG_HUGE)); // ignored
        loader.addToQueue(new Request(Settings.IMG_HUGE), new Point(3000, 3000)); // loaded and scaled in 2048x2048
        loader.addToQueue(new Request(Settings.IMG_FAKE)); // ignored
        loader.addToQueue(new Request(Settings.IMG_FAKE), new Point(300, 300)); // would fail - there is no image
        loader.start();

        // 3-4 - auto in AssertListener
        waitLoadingThreads(loader);
    }

    /**
     * Mixed download - both from cache and URI.
     */
    public void testMixedDownload() throws Exception {
        doTestMixedDownload(new ImageLoaderWrapper());
    }

    /**
     * Mixed download - both from cache and URI. Also use LRUCache instead of default.
     */
    public void testMixedDownloadLRUCache() throws Exception {
        final ImageLoaderWrapper loader = new ImageLoaderWrapper();
        loader.setCache(new LRUCache());

        doTestMixedDownload(loader);
    }

    /**
     * Mixed download - both from cache and URI.
     * 1) create a loader
     * 2) init queue with all images from Settings (some should be added several times and with scales)
     * 3) check loaded images dimensions and sizes
     * 4) check failed image (Settings.FAKE) (by URI)
     * 5) after loading small image, require it again - it should be loaded from cache in the same size
     * 6) after loading normal image, require it again with larger scale - it should be reloaded and recached
     */
    private void doTestMixedDownload(final ImageLoaderWrapper loader) throws Exception {
        // 1
        final int[][] assertValues = {{153, 200, 45016}, {1600, 1000, 483849},
                {399, 218, 136436}, {2000, 812, 1688679}};
        final int[] smallReload = {1}, normalReload = {1};
        loader.addLoadingListener(new LoadingAdapter2<Bitmap>() {
            @Override
            public void processFinished(Request request, byte[] rawData, Bitmap data) {
                super.processFinished(request, rawData, data);
                LogUtil.d(ImageLoaderTest.class, "bitmap loaded " + data.getWidth() + "x" + data.getHeight());

                final Settings.Img img = getImg(request);
                assertNotNull(img);

                switch (img) {
                    case SMALL:
                        // 3
                        assertImages(assertValues, request, rawData, data);
                        // 5
                        if (smallReload[0] == 1) {
                            loader.addToQueue(new Request(Settings.IMG_SMALL));
                            assertEquals(1, loader.getCache().count());
                            smallReload[0] = 0;
                        }
                        break;

                    case NORMAL:
                        if (normalReload[0] == 1) {
                            // 3
                            assertImages(assertValues, request, rawData, data);
                            // 6
                            assertEquals(2, loader.getCache().count());
                            loader.addToQueue(new Request(Settings.IMG_NORMAL), new Point(400, 400));
                            assertEquals(1, loader.getCache().count());
                            normalReload[0] = 0;
                        } else {
                            // 6
                            assertEquals(4, loader.getCache().count());
                            assertEquals(400, data.getWidth());
                            assertEquals(250, data.getHeight());
                            assertEquals(169972, rawData.length);
                        }
                        break;

                    default:
                        // 3
                        assertImages(assertValues, request, rawData, data);
                }
            }

            @Override
            public void processFailed(Request request, Exception e) {
                super.processFailed(request, e);
                // 4
                assertTrue(request.getURI().equals(Settings.IMG_FAKE));
            }
        });

        // 2
        loader.addToQueue(new Request(Settings.IMG_SMALL)); // would be ignored
        loader.addToQueue(new Request(Settings.IMG_SMALL), new Point(200, 200)); // would be loaded and scaled
        loader.addToQueue(new Request(Settings.IMG_SMALL)); // ignored
        loader.addToQueue(new Request(Settings.IMG_NORMAL)); // loaded
        loader.addToQueue(new Request(Settings.IMG_LARGE), new Point(400, 400)); // loaded and scaled
        loader.addToQueue(new Request(Settings.IMG_HUGE)); // ignored
        loader.addToQueue(new Request(Settings.IMG_HUGE), new Point(3000, 3000)); // loaded and scaled in 2048x2048
        loader.addToQueue(new Request(Settings.IMG_FAKE)); // ignored
        loader.addToQueue(new Request(Settings.IMG_FAKE), new Point(300, 300)); // would fail - there is no image
        assertEquals(5, loader.getQueueSize());
        loader.start();

        waitLoadingThreads(loader);
    }

    private void waitLoadingThreads(ImageLoaderWrapper loader) {
        //noinspection StatementWithEmptyBody
        while(loader.getLoaderThread2().isAlive() || loader.getCacheLoaderThread2().isAlive()) {}
    }

    private Settings.Img getImg(Request request) {
        for (Settings.Img img : Settings.Img.values())
            if (img.getURI().equals(request.getURI()))
                return img;

        return null;
    }

    private void assertImages(int[][] values, Request request, byte[] rawData, Bitmap data) {
        final Settings.Img img = getImg(request);
        assertNotNull(img);

        switch (img) {
            case SMALL:
                assertEquals(values[0][0], data.getWidth());
                assertEquals(values[0][1], data.getHeight());
                assertEquals(values[0][2], rawData.length);
                break;

            case NORMAL:
                assertEquals(values[1][0], data.getWidth());
                assertEquals(values[1][1], data.getHeight());
                assertEquals(values[1][2], rawData.length);
                break;

            case LARGE:
                assertEquals(values[2][0], data.getWidth());
                assertEquals(values[2][1], data.getHeight());
                assertEquals(values[2][2], rawData.length);
                break;

            case HUGE:
                assertEquals(values[3][0], data.getWidth());
                assertEquals(values[3][1], data.getHeight());
                assertEquals(values[3][2], rawData.length);
                break;

            default:
                throw new IllegalStateException("Should not be possible to load " + img.getURI());
        }
    }

    private class AssertListener extends LoadingAdapter2<Bitmap> {

        private int[][] values;

        public AssertListener(int[][] values) {
            this.values = values;
        }

        @Override
        public void processFinished(Request request, byte[] rawData, Bitmap data) {
            super.processFinished(request, rawData, data);
            LogUtil.d(ImageLoaderTest.class, "bitmap loaded " + data.getWidth() + "x" + data.getHeight());
            assertImages(values, request, rawData, data);
        }

        @Override
        public void processFailed(Request request, Exception e) {
            super.processFailed(request, e);
            assertTrue(request.getURI().equals(Settings.IMG_FAKE));
        }
    }

    private class ImageLoaderWrapper extends ImageLoader {
        public ImageLoaderWrapper() {
            super();
            setDebug(true);
            setFullAsyncMode(true);
        }

        public ImageLoaderWrapper(LoadingListener<Bitmap> listener) {
            super(listener);
            setDebug(true);
            setFullAsyncMode(true);
        }

        public boolean scaleLarger2(Point p1, Point p2) { return scaleLarger(p1, p2); }

        public Thread getLoaderThread2() { return getLoaderThread(); }
        public Thread getCacheLoaderThread2() { return getCacheLoaderThread(); }
    }
}
