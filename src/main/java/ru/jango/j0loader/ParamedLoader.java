package ru.jango.j0loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URISyntaxException;
import java.util.ArrayList;

import ru.jango.j0loader.part.Part;

/**
 * Parametrized loader. A loader, witch allows not only to download data, but also to upload it.
 * Parameters for sending should be backed as a {@link java.util.List}<{@link ru.jango.j0loader.part.Part}> and
 * passed inside a {@link ru.jango.j0loader.Request} object.
 *
 * @param <T>   after postprocessing of the loaded data an object of type T will be created and passed into
 *              {@link ru.jango.j0loader.DataLoader.LoadingListener#loadingFinished(Request, byte[], Object)}
 */
public abstract class ParamedLoader<T> extends DataLoader<T> {
    // TODO look through the List<Part> and use HTTP GET in certain cases (not always POST)

	@Override
	protected InputStream openInputStream(Request request) throws IOException, URISyntaxException {
		final HttpURLConnection urlConnection = (HttpURLConnection) request.getURL().openConnection();
        configURLConnection(urlConnection);
		sendParams(request, urlConnection);

		request.setContentLength(urlConnection.getContentLength());
		return urlConnection.getInputStream();
	}

    /**
     * Applies configurations to specified {@link java.net.HttpURLConnection}.
     */
	protected void configURLConnection(HttpURLConnection urlConnection) throws ProtocolException {
        super.configURLConnection(urlConnection);

		urlConnection.setDoOutput(true);
		urlConnection.setRequestMethod("POST");
		urlConnection.setRequestProperty("Connection", "Keep-Alive");
		urlConnection.setRequestProperty("Cache-Control", "no-cache");
		urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + Part.BOUNDARY);
	}
	
	private void sendParams(Request request, HttpURLConnection urlConnection) throws IOException, URISyntaxException {
		final OutputStream out = urlConnection.getOutputStream();          
		out.write((Part.HYPHENS + Part.BOUNDARY + Part.RN).getBytes("UTF-8"));
		
		// generate entities and count content length for uploading
        final ArrayList<byte[]> entities = new ArrayList<byte[]>();
        long totalBytes = 0;
		for (Part part : request.getRequestParams()) {
			entities.add(part.encodeEntity());
			totalBytes += entities.get(entities.size()-1).length;
		}
		
		logDebug("sendParams: " + request.getURI() + " : " 
					+ "entity count: " + entities.size() + "; " 
					+ "totalBytes: " + totalBytes + "bytes");
        
		writeEntities(request, out, entities, totalBytes);
        try { out.close(); } catch(Exception ignored) {}
	}
	
	/**
     * Write entities into specified {@link java.io.OutputStream}. Also automatically calls
     * {@link #postMainUploadingUpdateProgress(Request, long, long)} during the work; handles
     * {@link #canWork()} and {@link #isCurrentCancelled()} flags.
	 */
	protected void writeEntities(Request request, OutputStream out, ArrayList<byte[]> entities, long totalBytes) throws IOException {
		long progressLastUpdated = System.currentTimeMillis();
		int offset, count, uploadedBytes = 0;
		boolean updateProgress;

		for (byte[] entity : entities) {
			offset = 0;
			while (offset < entity.length && canWork() && !isCurrentCancelled()) {
				count = Math.min(entity.length - offset, BUFFER_SIZE_BYTES);
				out.write(entity, offset, count);
				offset += count;
				uploadedBytes += count;
				
				updateProgress = System.currentTimeMillis() > progressLastUpdated + PROGRESS_UPDATE_INTERVAL_MS;
				if (updateProgress) {
					progressLastUpdated = System.currentTimeMillis();
					postMainUploadingUpdateProgress(request, uploadedBytes, totalBytes);
				}
			}
			
		logDebug("writeEntities: " + request.getURI() + " : " + "entity wrote: " 
				+ (new String(entity, "UTF-8")));
		}
		
        out.flush();
	}
}
