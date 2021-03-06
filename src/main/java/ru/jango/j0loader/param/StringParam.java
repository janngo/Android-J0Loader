/*
 * The MIT License Copyright (c) 2014 Krayushkin Konstantin (jangokvk@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ru.jango.j0loader.param;

/**
 * A {@link Param} wrapper for plain text data. Default content type -
 * 'text/plain; charset=UTF-8'.
 */
public class StringParam extends Param {
	
	private String data;
	
	public StringParam(String name, String data) {
		setContentType("text/plain; charset=UTF-8");

		setData(data);
		setName(name);
	}
	
	@Override
	public Param setName(String name) {
		super.setName(name);
		setContentDisposition("form-data; name=\"" + name + "\"");
		
		return this;
	}

	public Param setData(String data) {
		this.data = data;
		try { setRawData(data.getBytes("UTF-8")); } 
		catch (Exception e) { setRawData(data.getBytes()); }
		
		return this;
	}

	public String getData() {
		return data;
	}

}
