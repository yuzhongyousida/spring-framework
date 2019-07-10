

package org.springframework.core.io;

import org.springframework.lang.Nullable;
import org.springframework.util.ResourceUtils;

/**
 * ResourceLoader统一了spring的资源加载定义的抽象
 * 统一资源定位
 */
public interface ResourceLoader {

	/**
	 * classPath前缀,默认为： "classpath:"
	 */
	String CLASSPATH_URL_PREFIX = ResourceUtils.CLASSPATH_URL_PREFIX;


	/**
	 * 返回指定资源位置的资源句柄
	 */
	Resource getResource(String location);

	/**
	 * 获取当前ResourceLoader使用（所属）的ClassLoader
	 */
	@Nullable
	ClassLoader getClassLoader();

}
