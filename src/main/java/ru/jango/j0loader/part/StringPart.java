package ru.jango.j0loader.part;

/**
 * A {@link ru.jango.j0loader.part.Part} wrapper for plain text data. Default content type -
 * 'text/plain; charset=UTF-8'.
 */
public class StringPart extends Part {
	
	private String data;
	
	public StringPart(String name, String data) {
		setContentType("text/plain; charset=UTF-8");

		setData(data);
		setName(name);
	}
	
	@Override
	public Part setName(String name) {
		super.setName(name);
		setContentDisposition("form-data; name=\"" + name + "\"");
		
		return this;
	}

	public Part setData(String data) {
		this.data = data;
		try { setRawData(data.getBytes("UTF-8")); } 
		catch (Exception e) { setRawData(data.getBytes()); }
		
		return this;
	}

	public String getData() {
		return data;
	}

}
