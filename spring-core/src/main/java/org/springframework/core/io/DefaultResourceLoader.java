
package org.springframework.core.io;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * ResourceLoader的默认实现类
 */
public class DefaultResourceLoader implements ResourceLoader {

	/**
	 * 当前资源使用的ClassLoader
	 */
	private ClassLoader classLoader;

	/**
	 * class类型区分的不同类型资源cache
	 */
	private final Set<ProtocolResolver> protocolResolvers = new LinkedHashSet<>(4);

	private final Map<Class<?>, Map<Resource, ?>> resourceCaches = new ConcurrentHashMap<>(4);


	/**
	 * 无参构造器
	 */
	public DefaultResourceLoader() {
		this.classLoader = ClassUtils.getDefaultClassLoader();
	}

	/**
	 * 有参构造器
	 */
	public DefaultResourceLoader(@Nullable ClassLoader classLoader) {
		this.classLoader = classLoader;
	}


	/**
	 * 指定classLoader属性
	 */
	public void setClassLoader(@Nullable ClassLoader classLoader) {
		this.classLoader = classLoader;
	}


	@Override
	public ClassLoader getClassLoader() {
		return (this.classLoader != null ? this.classLoader : ClassUtils.getDefaultClassLoader());
	}

	/**
	 * 注册用户自定义协议的资源定义
	 * 所以：提到如果要实现自定义 Resource，我们只需要继承 DefaultResource 即可，
	 *      但是有了 ProtocolResolver 后，我们不需要直接继承 DefaultResourceLoader，
	 *      改为实现 ProtocolResolver 接口也可以实现自定义的 ResourceLoader
	 * 然后调用DefaultResourceLoader的该方法就可以加入到spring的体系中来了
	 */
	public void addProtocolResolver(ProtocolResolver resolver) {
		Assert.notNull(resolver, "ProtocolResolver must not be null");
		this.protocolResolvers.add(resolver);
	}

	/**
	 * 获取用户自定义协议的资源定义列表
	 * @return
	 */
	public Collection<ProtocolResolver> getProtocolResolvers() {
		return this.protocolResolvers;
	}

	/**
	 * 根据给定的class查询resourceCaches中该类型下的所有资源信息map
	 */
	@SuppressWarnings("unchecked")
	public <T> Map<Resource, T> getResourceCache(Class<T> valueType) {
		// resourceCaches有valueType值的key，则返回其value，否则生成一个ConcurrentHashMap实例对象放入resourceCaches中并返回
		return (Map<Resource, T>) this.resourceCaches.computeIfAbsent(valueType, key -> new ConcurrentHashMap<>());
	}

	/**
	 * 清除缓存
	 */
	public void clearResourceCaches() {
		this.resourceCaches.clear();
	}


	/**
	 * 这个方法是ResourceLoader接口实现类中最核心的方法
	 * DefaultResourceLoader 对该方法提供了核心实现（因为，它的两个子类都没有提供覆盖该方法）
	 * 它根据提供的 location 返回相应的 Resource
	 * @param location
	 * @return
	 */
	@Override
	public Resource getResource(String location) {
		Assert.notNull(location, "Location must not be null");

		// 先从缓存中取
		for (ProtocolResolver protocolResolver : this.protocolResolvers) {
			Resource resource = protocolResolver.resolve(location, this);
			if (resource != null) {
				return resource;
			}
		}

		// resourcePath是以"/"开始的，则根据resourcePath获取其对应资源句柄
		if (location.startsWith("/")) {
			return getResourceByPath(location);
		}

		// resourcePath是以默认前缀"classpath:"开始的，则生成对应的ClassPathResource实例
		else if (location.startsWith(CLASSPATH_URL_PREFIX)) {
			return new ClassPathResource(location.substring(CLASSPATH_URL_PREFIX.length()), getClassLoader());
		}

		// 其他情况则认为是UrlResource
		else {
			try {
				// Try to parse the location as a URL...
				URL url = new URL(location);
				return new UrlResource(url);
			}
			catch (MalformedURLException ex) {
				// No URL -> resolve as resource path.
				return getResourceByPath(location);
			}
		}
	}

	/**
	 * 根据resourcePath获取其对应资源句柄
	 * 有些子类会覆盖掉该方法，以便影响到getResource返回的Resource实例对象的真实实现类，让其更加精确化，比如FileSystemResourceLoader
	 */
	protected Resource getResourceByPath(String path) {
		return new ClassPathContextResource(path, getClassLoader());
	}


	/**
	 * 通过继承实现ContextResource接口，显示的表示一个path上线文相关的ClassPathResource
	 */
	protected static class ClassPathContextResource extends ClassPathResource implements ContextResource {

		public ClassPathContextResource(String path, @Nullable ClassLoader classLoader) {
			super(path, classLoader);
		}

		@Override
		public String getPathWithinContext() {
			return getPath();
		}

		@Override
		public Resource createRelative(String relativePath) {
			String pathToUse = StringUtils.applyRelativePath(getPath(), relativePath);
			return new ClassPathContextResource(pathToUse, getClassLoader());
		}
	}

}
