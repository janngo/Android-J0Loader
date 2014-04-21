package ru.jango.j0loader.part;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Base class for managing HTTP request params. You can use this class and it's subclasses to
 * construct and manipulate HTTP-entities that you need. All aspects (including ContentType) could
 * be configured as you need.
 * <br><br>
 *
 * If you want a {@link ru.jango.j0loader.part.Part}-like wrapper (like
 * {@link ru.jango.j0loader.part.BitmapPart}) for some complicated structure (like
 * {@link android.graphics.Bitmap}), it's a good idea to subclass {@link ru.jango.j0loader.part.Part}.
 */
public class Part {

    public static final String RN = "\r\n";
    public static final String HYPHENS = "--";
    public static final String BOUNDARY = "******";

    private byte[] rawData;
	private String name;
	private String contentType;
	private String contentDisposition;

	public byte[] getRawData() {
		return rawData;
	}

	public String getName() {
		return name;
	}

	public String getContentType() {
		return contentType;
	}

	public int getContentLength() {
		if (rawData == null) return 0;
		return rawData.length;
	}

	public String getContentDisposition() {
		return contentDisposition;
	}

	public Part setRawData(byte[] rawData) {
		this.rawData = rawData;
		return this;
	}

	public Part setName(String name) {
		this.name = name;
		return this;
	}

	public Part setContentType(String contentType) {
		this.contentType = contentType;
		return this;
	}

	public Part setContentDisposition(String contentDisposition) {
		this.contentDisposition = contentDisposition;
		return this;
	}

	/**
	 * Writes this {@link ru.jango.j0loader.part.Part} into the specified {@link java.io.OutputStream}
     * as an HTTP-entity (with all headers, Black-Jack and others...)
     *
     * @see #encodeEntity()
	 */
	public void writeToStream(OutputStream out) throws IOException {
		out.write(encodeEntity());
	}

	/**
	 * Transforms this {@link ru.jango.j0loader.part.Part} into an HTTP-entity of full value - adds all
     * needed headers and boundaries before and after and transforms all this into a byte array.
	 */
	public byte[] encodeEntity() {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] entity = null;
		
		try {
			out.write(("Content-Disposition: " + getContentDisposition() + RN).getBytes("UTF-8"));
			out.write(("Content-Type: " + getContentType() + RN).getBytes("UTF-8"));
			out.write(("Content-Length: " + getContentLength() + RN).getBytes("UTF-8"));
			out.write(RN.getBytes("UTF-8"));
			out.write(rawData);
			out.write(RN.getBytes("UTF-8"));
			out.write((HYPHENS + BOUNDARY + RN).getBytes("UTF-8"));
			out.flush();
			
			entity = out.toByteArray();
		} catch (Exception ignored) {}
		finally { try { out.close(); } catch(Exception ignored) {} }
		
		return entity;
	}
	
}
