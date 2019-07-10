
package org.springframework.core.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * AbstractResource的其中一个实现类，对java.io.File类型资源的封装，处理文件资源的Resource实现类
 * 支持文件和 URL 的形式，同时也实现了WritableResource 接口
 */
public class FileSystemResource extends AbstractResource implements WritableResource {

	private final File file;

	private final String path;


	/**
	 * 构造器
	 */
	public FileSystemResource(File file) {
		Assert.notNull(file, "File must not be null");
		this.file = file;
		this.path = StringUtils.cleanPath(file.getPath());
	}

	/**
	 * 构造器
	 */
	public FileSystemResource(String path) {
		Assert.notNull(path, "Path must not be null");
		this.file = new File(path);
		this.path = StringUtils.cleanPath(path);
	}


	/**
	 * 返回资源的 filePath
	 */
	public final String getPath() {
		return this.path;
	}

	/**
	 * 文件资源的存在性校验
	 */
	@Override
	public boolean exists() {
		return this.file.exists();
	}

	/**
	 * 可读性校验
	 */
	@Override
	public boolean isReadable() {
		return (this.file.canRead() && !this.file.isDirectory());
	}

	/**
	 * 获取文件资源的InputStream实例
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return new FileInputStream(this.file);
	}

	/**
	 * 是否可写校验
	 */
	@Override
	public boolean isWritable() {
		return (this.file.canWrite() && !this.file.isDirectory());
	}

	/**
	 * 获取文件资源的FileOutputStream实例
	 */
	@Override
	public OutputStream getOutputStream() throws IOException {
		return new FileOutputStream(this.file);
	}

	/**
	 * 文件资源的URL
	 */
	@Override
	public URL getURL() throws IOException {
		return this.file.toURI().toURL();
	}

	/**
	 * 文件资源的URI
	 */
	@Override
	public URI getURI() throws IOException {
		return this.file.toURI();
	}

	/**
	 * 是否是file校验
	 */
	@Override
	public boolean isFile() {
		return true;
	}

	/**
	 * 返回file句柄
	 */
	@Override
	public File getFile() {
		return this.file;
	}

	/**
	 * 获取文件的nio读channel
	 */
	@Override
	public ReadableByteChannel readableChannel() throws IOException {
		return new FileInputStream(this.file).getChannel();
	}

	/**
	 * 获取文件的nio写channel
	 */
	@Override
	public WritableByteChannel writableChannel() throws IOException {
		return new FileOutputStream(this.file).getChannel();
	}

	/**
	 * 文件长度大小
	 */
	@Override
	public long contentLength() throws IOException {
		return this.file.length();
	}

	/**
	 * 用给定路径应用于当前文件资源的资源描述上
	 */
	@Override
	public Resource createRelative(String relativePath) {
		String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
		return new FileSystemResource(pathToUse);
	}

	@Override
	public String getFilename() {
		return this.file.getName();
	}

	@Override
	public String getDescription() {
		return "file [" + this.file.getAbsolutePath() + "]";
	}


	@Override
	public boolean equals(Object obj) {
		return (obj == this ||
			(obj instanceof FileSystemResource && this.path.equals(((FileSystemResource) obj).path)));
	}

	@Override
	public int hashCode() {
		return this.path.hashCode();
	}

}
