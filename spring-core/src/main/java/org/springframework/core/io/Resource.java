/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.io;

import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Resource统一了spring中的资源定义
 * Resource是Spring 框架所有资源的抽象和访问接口，继承自InputStreamSource，作为所有资源的统一抽象
 * Resource定义了一些通用的方法，有子类AbstractResource提供统一的默认实现
 *
 * @author Juergen Hoeller
 * @since 28.12.2003
 * @see #getInputStream()
 * @see #getURL()
 * @see #getURI()
 * @see #getFile()
 * @see WritableResource
 * @see ContextResource
 * @see UrlResource
 * @see ClassPathResource
 * @see FileSystemResource
 * @see PathResource
 * @see ByteArrayResource
 * @see InputStreamResource
 */
public interface Resource extends InputStreamSource {

	/**
	 * 资源是否存在
	 */
	boolean exists();

	/**
	 * 资源是否刻度
	 */
	default boolean isReadable() {
		return true;
	}

	/**
	 * 资源所代表的句柄是否被一个stream打开了
	 */
	default boolean isOpen() {
		return false;
	}

	/**
	 * 资源是否是文件
	 */
	default boolean isFile() {
		return false;
	}

	/**
	 * 返回资源的URL的句柄
	 */
	URL getURL() throws IOException;

	/**
	 * 返回资源的URI的句柄
	 */
	URI getURI() throws IOException;

	/**
	 * 返回资源file的句柄
	 */
	File getFile() throws IOException;

	/**
	 * 返回ReadableByteChannel
	 */
	default ReadableByteChannel readableChannel() throws IOException {
		return Channels.newChannel(getInputStream());
	}

	/**
	 * 资源内容的长度
	 */
	long contentLength() throws IOException;

	/**
	 * 资源最后修改时间
	 */
	long lastModified() throws IOException;

	/**
	 * 根据资源的相对路径创建新资源
	 */
	Resource createRelative(String relativePath) throws IOException;

	/**
	 * 资源文件名
	 */
	@Nullable
	String getFilename();

	/**
	 * 资源描述
	 */
	String getDescription();

}
