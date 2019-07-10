
package org.springframework.core.io.support;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 *
 * ResourceLoader接口的普通实现类，重写的Resource getResource(String location) 方法，
 * 每次只能根据location返回一个Resource，当需要加载多个的时候，
 * 我们除了多次调用getResource(String location)方法，别无他法，
 * ResourcePatternResolver接口基于ResourceLoader进行了扩展，
 * 它支持根据指定的资源路径匹配模式每次返回多个 Resource 实例
 *
 * @author Juergen Hoeller
 * @since 1.0.2
 * @see org.springframework.core.io.Resource
 * @see org.springframework.core.io.ResourceLoader
 * @see org.springframework.context.ApplicationContext
 * @see org.springframework.context.ResourceLoaderAware
 */
public interface ResourcePatternResolver extends ResourceLoader {

	/**
	 * 扩展的classPath前缀： "classpath*:"（全文检索匹配资源的时候用该前缀）
	 * 这不同于ResourceLoader的类路径URL前缀，因为它
	 * 检索给定名称的所有匹配资源（例如“/beans.xml”），例如，在所有已部署JAR文件的根目录中
	 */
	String CLASSPATH_ALL_URL_PREFIX = "classpath*:";

	/**
	 * 获取所有资源URL匹配locationPattern的资源对象数组
	 * @param locationPattern the location pattern to resolve
	 * @return the corresponding Resource objects
	 * @throws IOException in case of I/O errors
	 */
	Resource[] getResources(String locationPattern) throws IOException;

}
