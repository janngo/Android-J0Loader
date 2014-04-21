package ru.jango.j0loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;

import ru.jango.j0loader.queue.DefaultQueue;
import ru.jango.j0loader.queue.Queue;
import ru.jango.j0util.LogUtil;

/**
 * Base abstract loader class. Capabilities:
 * <ul>
 * <li>load data itself (HTTP GET without sending params, file, ftp, jar - see {@link java.net.URLConnection})</li>
 * <li>report into main thread by {@link ru.jango.j0loader.DataLoader.LoadingListener} (protected postMain...() methods)</li>
 * <li>provides one {@link java.lang.Thread} for asynchronous queue execution ({@link #loadInBackground(Request)})</li>
 * <li>control thread execution ({@link #canWork()}, {@link #cancelCurrent()})</li>
 * <li>control queue execution ({@link #getQueue()})</li>
 * </ul>
 * <br>
 *
 * Uses Java listeners model (classical Observer pattern) - many
 * {@link ru.jango.j0loader.DataLoader.LoadingListener}s could listen to one loader; and loader
 * reports to all listeners about all events. <b>Each listener should check itself, whether the data
 * passed into it's methods is valid for it, or should be ignored.</b> It could be done with help of the
 * {@link ru.jango.j0loader.Request} objects, witch are passed into each
 * {@link ru.jango.j0loader.DataLoader.LoadingListener}'s methods.
 */
public abstract class DataLoader<T> {
	protected final int PROGRESS_UPDATE_INTERVAL_MS = 200;
	protected final int BUFFER_SIZE_BYTES = 50;
    protected final int CONNECT_TIMEOUT = 15000;
    protected final int READ_TIMEOUT = 10000;

	private Handler mainThreadHandler;
	private Set<LoadingListener<T>> listeners;

	private Thread loaderThread;
	private Queue queue;
	private boolean working;        // TRUE if the queue is executing
	private boolean cancelled;      // TRUE if processing current Request should be stopped
	private boolean debug;          // TRUE if debug messages should be logged
	
	public DataLoader() {
		mainThreadHandler = new Handler();
		listeners = new HashSet<LoadingListener<T>>();
	}
	
	protected void logDebug(String message) {
		if (isDebug())
			LogUtil.d(getClass(), message);
	}

    /**
     * Checks network connection.
     */
    public static boolean isOnline(Context context)  {
        final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo nInfo = cm.getActiveNetworkInfo();

        return (nInfo != null && nInfo.isConnected());
    }

    ////////////////////////////////////////////////////////////////////////
    //
    //		Setters and getters
    //
    ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Adds new listener. A {@link ru.jango.j0loader.DataLoader.LoadingListener} object should be
     * unique - you can't add a single listener twice.
	 * 
	 * @param listener  new loading listener
	 */
	public void addLoadingListener(LoadingListener<T> listener) {
		listeners.add(listener);
	}
	
	/**
	 * Removes a certain {@link ru.jango.j0loader.DataLoader.LoadingListener}.
	 * 
	 * @param listener  loading listener to remove
	 */
	public void removeLoadingListener(LoadingListener<T> listener) {
		listeners.remove(listener);
	}

    /**
     * Attempts to stop a queue execution. It doesn't actually stops the execution - only sets an
     * internal flag to FALSE. Various longrunning methods check this flag and stop themselves.
     * <br><br>
     * Calling this method won't stop queue immediately, but all operations will die soon after it.
     */
    public void stopWorking() {
        working = false;
    }

	/**
     * Sets internal flag to TRUE, that indicates that queue can be executed. Method doesn't actually
     * starts the execution ({@link #start()} does), it only allows execution.
	 */
	public void allowWorking() {
		working = true;
	}
	
	/**
	 * Checks if queue execution is allowed.
	 */
	public boolean canWork() {
        return working;
	}

	/**
     * Attempts to stop downloading the current queue element. It doesn't actually stops the
     * downloading - only sets an internal flag. The downloading loop than this flag and stops
     * itself.
     * <br><br>
     * Calling this method won't stop downloading, but the operation will die soon after it.
	 */
	public void cancelCurrent() {
		cancelled = true;
	}

    /**
     * Check if debug logging is on.
     */
	public boolean isDebug() {
		return debug;
	}

    /**
     * Switches debug logging on/off.
     */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

    /**
     * Returns a {@link java.lang.Thread} where the queue will be executed. In subclasses this
     * method could be overwritten to provide another thread.
     */
    protected Thread getLoaderThread() {
        if (!(loaderThread!=null && loaderThread.isAlive()))
            return loaderThread = new Thread(queueRunnable);

        return loaderThread;
    }

    ////////////////////////////////////////////////////////////////////////
    //
    //		Queue controlling methods
    //
    ////////////////////////////////////////////////////////////////////////

    /**
     * Adds a {@link ru.jango.j0loader.Request} into the end of the loading queue and <b>automatically
     * starts the execution of the queue</b>.
     *
     * @param request   a {@link Request} to add
     */
    public void addToQueue(Request request) {
        getQueue().add(request);
        start();
    }

    /**
     * Adds all {@link ru.jango.j0loader.Request}s into the end of the loading queue and <b>automatically
     * starts the execution of the queue</b>.
     *
     * @param requests   a pack of {@link Request}s to add
     */
    public void addToQueue(Collection<Request> requests) {
        getQueue().addAll(requests);
        start();
    }

    /**
     * Removes a {@link Request} from the loading queue. If this {@link ru.jango.j0loader.Request} is
     * already being processed, it could be retrieved by {@link #getCurrentQueueElement()} and the
     * procession could be stopped by {@link #cancelCurrent()} (stops only the current, not all queue).
     *
     * @param request   a {@link Request} to remove
     */
    public void removeFromQueue(Request request) {
        getQueue().remove(request);
    }

    /**
     * Returns current element (witch is processed now) or null.
     */
    public Request getCurrentQueueElement() {
        return getQueue().current();
    }

    /**
     * Clears the loading queue.
     */
    public void clearQueue() {
        getQueue().clear();
    }

    /**
     * Checks if the queue is empty.
     */
    public boolean isQueueEmpty() {
        return getQueue().isEmpty();
    }

    /**
     * Actually starts the execution of the queue in a separate {@link java.lang.Thread}.
     */
    public void start()  {
        allowWorking();

        final Thread thread = getLoaderThread();
        if (!thread.isAlive()) thread.start();
    }

    /**
     * Resets the loader to it's initial clear state: <br>
     * <ul>
     * <li>stop operations by {@link #stopWorking()}</li>
     * <li>clear queue by {@link #clearQueue()}</li>
     * </ul>
     */
    public void reset() {
        stopWorking();
        clearQueue();
    }

    /**
     * Special method for queue configuration. By default {@link ru.jango.j0loader.DataLoader}
     * creates an instance of {@link ru.jango.j0loader.queue.DefaultQueue}, but if a queue with
     * different logic is required, it could be substituted here.
     * <br><br>
     * This method with conjunction of {@link ru.jango.j0loader.queue.Queue} hierarchy defines a
     * usual Iterator pattern.
     *
     * @return  loading queue instance
     */
    protected Queue getQueue() {
        if (queue == null) queue = new DefaultQueue();
        return queue;
    }

    ////////////////////////////////////////////////////////////////////////
    //
    //		Loading methods
    //
    ////////////////////////////////////////////////////////////////////////
	
	/**
     * Does the subclass-specific loading operations - loading itself and postprocessing. It is
     * called from a separate thread.
	 *
	 * @param request	{@link Request} from the loading queue
	 */
	protected abstract void loadInBackground(Request request) throws Exception;

	/**
     * Helper method for subclasses - opens an {@link java.io.InputStream} and does the loading.
	 *
	 * @return  raw just loaded data
	 */
	protected byte[] load(Request request) throws IOException, URISyntaxException {
		InputStream in = null;
		try {
			in = openInputStream(request);
			return doLoad(request,in);
		} finally {
            try {
                assert in != null;
                in.close();
            } catch(Exception ignored) {}
        }
	}
	
    /**
	 * Helper method for subclasses - actually does the loading. Also automatically calls
     * {@link #postMainLoadingUpdateProgress(Request, long, long)} during the work; handles
     * 'working' and 'cancelled' flags.
	 */
	protected byte[] doLoad(Request request, InputStream in) throws IOException {
        cancelled = false;
		long progressLastUpdated = System.currentTimeMillis();
		int nRead, totalRead = 0;
		byte[] data = new byte[BUFFER_SIZE_BYTES];

		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		while ((nRead = in.read(data, 0, data.length))!=-1 && canWork() && !cancelled)  {
			buffer.write(data, 0, nRead);
			totalRead += nRead;
			
			boolean updateProgress = System.currentTimeMillis() > progressLastUpdated + PROGRESS_UPDATE_INTERVAL_MS;
			if (request.getContentLength()!=-1 && updateProgress) {
				progressLastUpdated = System.currentTimeMillis();
				postMainLoadingUpdateProgress(request,totalRead,request.getContentLength());
			}
		}
		buffer.flush();
		
		final byte[] ret = buffer.toByteArray();
		buffer.close();
        logDebug("doLoad: " + request.getURI() + " : " + (new String(ret, "UTF-8"))); 
        if (cancelled) {
		    cancelled = false;
            return null;
        } else return ret;
    }

	/**
     * Helper method for subclasses - actually opens an {@link java.io.InputStream} and sets
     * content length inside the passed {@link ru.jango.j0loader.Request} object -
     * {@link ru.jango.j0loader.Request#setContentLength(long)}.
	 */
	protected InputStream openInputStream(Request request) throws IOException, URISyntaxException {
		final URLConnection urlConnection = request.getURL().openConnection();
		urlConnection.setUseCaches(false);
		urlConnection.setDoInput(true);
		urlConnection.setDoOutput(false);
		urlConnection.setConnectTimeout(CONNECT_TIMEOUT);
		urlConnection.setReadTimeout(READ_TIMEOUT);

        // TODO check getContentLength() with File sheme
		request.setContentLength(urlConnection.getContentLength());
		return urlConnection.getInputStream();
	}
	
    ////////////////////////////////////////////////////////////////////////
    //
    //		Crossthread communication methods
    //
    ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Reports on main thread to all listeners that executing of the specified
     * {@link ru.jango.j0loader.Request} has just began.
     *
     * @param request   {@link ru.jango.j0loader.Request} processed now
	 */
	protected void postMainLoadingStarted(final Request request) {
		if (!canWork()) return;
		logDebug("postMainLoadingStarted: " + request.getURI());
		
		mainThreadHandler.post(new Runnable() {
			@Override
			public void run() { 
				for (LoadingListener<T> listener : listeners)
					listener.processStarted(request); 
			}
		});
	}
		
	/**
	 * Reports on main thread to all listeners that a new chunk of data has been downloaded for
     * the specified {@link ru.jango.j0loader.Request}.
     *
     * @param request       {@link ru.jango.j0loader.Request} processed now
     * @param loadedBytes   total bytes that have already been downloaded
     * @param totalBytes    total bytes that should be downloaded (content length)
	 */
	protected void postMainLoadingUpdateProgress(final Request request, final long loadedBytes, final long totalBytes) {
		if (!canWork()) return;
		logDebug("postMainLoadingUpdateProgress: " + request.getURI() + " : " 
					+ "downloaded " + loadedBytes + "bytes; "
					+ "total " + totalBytes + "bytes");
		
		mainThreadHandler.post(new Runnable()  {
			@Override
			public void run()  { 
				for (LoadingListener<T> listener : listeners)
					listener.loadingUpdateProgress(request, loadedBytes, totalBytes); 
			}
		});
	}

    /**
     * Reports on main thread to all listeners that a new chunk of data has been uploaded during
     * processing the specified {@link ru.jango.j0loader.Request}. It could be called with HTTP
     * POST and GET requests while sending params.
     *
     * @param request       {@link ru.jango.j0loader.Request} processed now
     * @param uploadedBytes total bytes that have already been uploaded
     * @param totalBytes    total bytes that should be uploaded (sum of all params sizes)
     */
	protected void postMainUploadingUpdateProgress(final Request request, final long uploadedBytes, final long totalBytes) {
		if (!canWork()) return;
		logDebug("postMainUploadingUpdateProgress: " + request.getURI() + " : " 
					+ "uploaded " + uploadedBytes + "bytes; "
					+ "total " + totalBytes + "bytes");
		
		mainThreadHandler.post(new Runnable()  {
			@Override
			public void run()  { 
				for (LoadingListener<T> listener : listeners)
					listener.uploadingUpdateProgress(request, uploadedBytes, totalBytes); 
			}
		});
	}
	
	/**
	 * Reports on main thread to all listeners that executing of the specified
     * {@link ru.jango.j0loader.Request} has just successfully finished.
     *
     * @param request   {@link ru.jango.j0loader.Request} that had just been processed
     * @param rawData   raw bytes of the downloaded data
     * @param data      postprocessed loader-specific data
     */
	protected void postMainLoadingFinished(final Request request, final byte[] rawData, final T data) {
		if (!canWork()) return;
		logDebug("postMainLoadingFinished: " + request.getURI() + " : " 
					+ rawData.length + "bytes");
		
		mainThreadHandler.post(new Runnable()  {
			@Override
			public void run()  { 
				for (LoadingListener<T> listener : listeners)
					listener.loadingFinished(request,rawData,data); 
			}
		});
	}

    /**
     * Reports on main thread to all listeners that executing of the specified
     * {@link ru.jango.j0loader.Request} has just failed.
     *
     * @param request   {@link ru.jango.j0loader.Request} that had just failed
     * @param e         raised {@link Exception}
     */
	protected void postMainLoadingFailed(final Request request, final Exception e) {
		if (!canWork()) return;
		if (isDebug()) e.printStackTrace();
		logDebug("postMainLoadingFailed: " + request.getURI() + " : " + e);
		
		mainThreadHandler.post(new Runnable() {
			@Override
			public void run() { 
				for (LoadingListener<T> listener : listeners)
					listener.processFailed(request,e); 
			}
		});
	}
	
    ////////////////////////////////////////////////////////////////////////
    //
    //		Crossthread communication staff
    //
    ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Listener interface for loading (uploading and downloading) process.
	 */
	public interface LoadingListener<T> {
		/**
         * Called before the loading process started. Process can be divided into uploading and
         * downloading (HTTP POST and GET), or just downloading (other protocols).
		 *
         * @param request   {@link ru.jango.j0loader.Request} that is processed now
		 */
		public void processStarted(Request request);

		/**
		 * Called while the params are uploaded.
		 *
         * @param request       {@link ru.jango.j0loader.Request} that is processed now
		 * @param uploadedBytes	total bytes that have already been uploaded
		 * @param totalBytes	total bytes that should be uploaded (sum of all params sizes)
		 */
		public void uploadingUpdateProgress(Request request, long uploadedBytes, long totalBytes);
		
		/**
		 * Called while the data is downloaded.
		 *
         * @param request       {@link ru.jango.j0loader.Request} that is processed now
		 * @param loadedBytes	total bytes that have already been downloaded
		 * @param totalBytes	total bytes that should be downloaded (content length)
		 */
		public void loadingUpdateProgress(Request request, long loadedBytes, long totalBytes);

		/**
		 * Called when the loading had been successfully finished.
		 *
         * @param request   {@link ru.jango.j0loader.Request} that had just successfully finished
		 * @param rawData	raw downloaded data
		 * @param data		postprocessed loader-specific data
		 */
		public void loadingFinished(Request request, byte[] rawData, T data);
		
		/**
		 * Called when the loading has failed (both while uploading and downloading).
		 *
         * @param request   {@link ru.jango.j0loader.Request} that had just failed
		 * @param e			raised {@link java.lang.Exception}
		 */
		public void processFailed(Request request, Exception e);
	}

    /**
     * Main runnable witch executes asynchronously.
     */
	private Runnable queueRunnable = new Runnable() {
		@Override
		public void run()  {
			while (!isQueueEmpty() && canWork()) {
				final Request request = getQueue().next();

				try {
					postMainLoadingStarted(request);
					loadInBackground(request);
				} catch (Exception e) { postMainLoadingFailed(request, e); }

				LogUtil.logMemoryUsage();
			}
		}
	};
}
