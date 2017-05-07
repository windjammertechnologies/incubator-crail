package com.ibm.crail;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;

class MultiStreamNonBlocking extends BufferedInputStream implements CrailMultiStream {
	private CrailFS fs;
	private Iterator<String> paths;
	private ArrayBlockingQueue<CrailInputStream> readyStreams;
	private ArrayBlockingQueue<CrailInputStream> finalStreams;
	
	MultiStreamNonBlocking(CrailFS fs, Iterator<String> paths, int outstanding, int files) throws Exception {
		super(fs, outstanding, 0);
		this.fs = fs;
		this.paths = paths;
		this.readyStreams = new ArrayBlockingQueue<CrailInputStream>(outstanding);
		this.finalStreams = new ArrayBlockingQueue<CrailInputStream>(outstanding);
	}

	@Override
	public CrailInputStream getStream() throws Exception {
		while(readyStreams.isEmpty() && paths.hasNext()){
			String path = paths.next();
			CrailFile file = fs.lookup(path).get().asFile();
			if (file == null){
				throw new Exception("File not found, name " + path);
			}
			if (file.getCapacity() > 0){
				CrailInputStream stream = file.getDirectInputStream(file.getCapacity());
				readyStreams.add(stream);
			}
		}
		return readyStreams.peek();
	}
	
	
	public void putStream() throws Exception {
		CrailInputStream stream = readyStreams.peek();
		if (stream.position() >= stream.getFile().getCapacity()) {
			stream = readyStreams.poll();
			finalStreams.add(stream);
		}
	}

	@Override
	public void close() throws IOException {
		super.close();
		try {
			while(!readyStreams.isEmpty()){
				finalStreams.add(readyStreams.poll());
			}
			while(paths.hasNext()){
				String path = paths.next();
				CrailFile file = fs.lookup(path).get().asFile();
				if (file != null){
					CrailInputStream stream = file.getDirectInputStream(file.getCapacity());
					finalStreams.add(stream);
				}
			}
			while(!finalStreams.isEmpty()){
				CrailInputStream stream = finalStreams.poll();
				stream.close();
			}
		} catch(Exception e){
			throw new IOException(e);
		}
	}

	@Override
	public void seek(long pos) throws IOException {
		throw new IOException("Seek not supported on multistream");
	}	
	
}
