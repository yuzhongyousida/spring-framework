/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.context.request.async;

import org.springframework.lang.Nullable;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * 异步web request接口
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public interface AsyncWebRequest extends NativeWebRequest {

	/**
	 * 设置完成并发处理所需的时间。当并行处理正在进行时，不应设置此属性。
	 */
	void setTimeout(@Nullable Long timeout);

	/**
	 * 添加请求处理过程结束之后的有超时时间的handler线程
	 */
	void addTimeoutHandler(Runnable runnable);

	/**
	 * 添加请求处理过程结束之后的handler线程
	 */
	void addCompletionHandler(Runnable runnable);

	/**
	 * 开启异步请求处理过程
	 */
	void startAsync();

	/**
	 * 请求的异步回调是否开始
	 * 如果异步处理从未启动、已完成或者请求进一步处理，则返回“false”
	 */
	boolean isAsyncStarted();

	/**
	 * 将request分发到容器中以便同一应用线程中的并发任务完成之后继续执行
	 */
	void dispatch();

	/**
	 * 异步处理过程是否完成
	 */
	boolean isAsyncComplete();

}
