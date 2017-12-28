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

package org.springframework.web.servlet;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SourceFilteringListener;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.CallableProcessingInterceptorAdapter;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.ServletRequestHandledEvent;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.util.NestedServletException;
import org.springframework.web.util.WebUtils;

/**
 * Spring Web框架的基本servlet。在基于JavaBean的整体解决方案中提供与Spring应用程序上下文的集成
 *
 * 该类提供一下功能：
 * 1、为每个servlet管理一个WebApplicationContext（org.springframework.web.context.WebApplicationContext）实例。
 *    servlet的配置是由servlet的命名空间中的bean决定的
 *
 * 2、在请求处理过程中发布events，不管是否成功处理了请求。
 *
 *  子类必须实现doService()方法以处理请求。因为这不是继承HttpServletBean而是直接继承HttpServlet的方法，bean的属性被自动映射到它。
 *  子类可以重写initFrameworkServlet()方法，以便自定义初始化。
 *
 *  检测servlet初始化level的"contextClass"参数，如果没有检测到，则使用默认的XmlWebApplicationContext
 *  注意：若用默认FrameworkServlet，自定义上下文类需要实现ConfigurableWebApplicationContext（org.springframework.web.context.ConfigurableWebApplicationContext）的接口
 *
 *  接受一个可选的“contextinitializerclasses“作为servlet初始化参数，指定一个或多个applicationcontextinitializer类。
 *  托管的Web应用程序的上下文将授权给这些初始化列表，允许额外的程序配置，例如增加property来源或在ConfigurableApplicationContext.getEnvironment()之前激活配置文件
 *
 *  通过“contextconfiglocation“这个servlet初始化参数初始servlet上下文实例，多个文件路径可以由任意数量的逗号和空格分隔，如“test-servlet.xml，myservlet .xml”。
 *  如果未显式指定，则上下文实现应该在servlet的命名空间中构建一个默认位置
 *
 *  注意：在配置多个位置的情况下，后来的bean定义将覆盖前面加载的文件中定义的，至少在使用Spring的ApplicationContext实现默认时是这样的。
 *  可以利用它通过一个额外的XML文件故意重写某些bean定义
 *
 *  默认命名空间是"servlet名称-servlet", 比如"test-servlet"的servlet名称是test（导致一个XmlWebApplicationContext生成的"/WEB-INF/test-servlet.xml"默认位置）
 *  命名空间也可以通过显式设置"namespace"servlet初始化参数的值来进行设置
 *
 *  自spring3.1起，FrameworkServlet现在可以注入一个web application context，而不是自己内部生成一个。
 *  这在servlet 3.0以上环境是非常有用的，可以支持servlet实例的编程式注册，详见FrameworkServlet(WebApplicationContext)构造方法的文档细节
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Chris Beams
 * @author Rossen Stoyanchev
 * @author Phillip Webb
 * @see #doService
 * @see #setContextClass
 * @see #setContextConfigLocation
 * @see #setContextInitializerClasses
 * @see #setNamespace
 */
@SuppressWarnings("serial")
public abstract class FrameworkServlet extends HttpServletBean implements ApplicationContextAware {

	/**
	 * WebApplicationContext 命令空间的后缀
	 */
	public static final String DEFAULT_NAMESPACE_SUFFIX = "-servlet";

	/**
	 * FrameworkServlet的默认上下文类（org.springframework.web.context.support.XmlWebApplicationContext）
	 */
	public static final Class<?> DEFAULT_CONTEXT_CLASS = XmlWebApplicationContext.class;

	/**
	 * WebApplicationContext在ServletContext属性中的属性前缀，最后是servlet的名称
	 */
	public static final String SERVLET_CONTEXT_PREFIX = FrameworkServlet.class.getName() + ".CONTEXT.";

	/**
	 * 初始化参数值的多值之间的分隔符
	 */
	private static final String INIT_PARAM_DELIMITERS = ",; \t\n";


	/** 在ServletContext属性中WebApplicationContext属性的标识key名属性 */
	private String contextAttribute;

	/** WebApplicationContext实现类来创建 */
	private Class<?> contextClass = DEFAULT_CONTEXT_CLASS;

	/** WebApplicationContext id */
	private String contextId;

	/** servlet的命名空间 */
	private String namespace;

	/** 显式上下文配置文件的位置属性 */
	private String contextConfigLocation;

	/** 实际应用于上下文的applicationcontextinitializer实例 */
	private final List<ApplicationContextInitializer<ConfigurableApplicationContext>> contextInitializers = new ArrayList<>();

	/** 初始化参数中设置ApplicationContextInitializer的类名字符串（若有多个，逗号隔开） */
	private String contextInitializerClasses;

	/** 是否把上下文公开为一个ServletContext属性的标识 */
	private boolean publishContext = true;

	/** 是否应该在每次请求结束时公开servletrequesthandledevent标识 */
	private boolean publishEvents = true;

	/** 是否暴露localecontext和requestattributes作为可继承的子线程标识 */
	private boolean threadContextInheritable = false;

	/** 是否将HTTP OPTIONS 请求dispatch给doService()方法标识 */
	private boolean dispatchOptionsRequest = false;

	/** 是否将HTTP TRACE 请求dispatch给doService()方法标识 */
	private boolean dispatchTraceRequest = false;

	/** WebApplicationContext */
	private WebApplicationContext webApplicationContext;

	/** webApplicationContext属性是否是通过setApplicationContext()注入的标识 */
	private boolean webApplicationContextInjected = false;

	/** 用来检测onRefresh()方法是否已经被调用的标识 */
	private boolean refreshEventReceived = false;


	/**
	 * 无参构造器
	 */
	public FrameworkServlet() {
	}


	public FrameworkServlet(WebApplicationContext webApplicationContext) {
		this.webApplicationContext = webApplicationContext;
	}





	/**
	 * 重写父类HttpServletBean中的方法, 在设置了bean属性之后调用。创建该servlet的WebApplicationContext
	 */
	@Override
	protected final void initServletBean() throws ServletException {
		getServletContext().log("Initializing Spring FrameworkServlet '" + getServletName() + "'");
		if (this.logger.isInfoEnabled()) {
			this.logger.info("FrameworkServlet '" + getServletName() + "': initialization started");
		}

		long startTime = System.currentTimeMillis();

		try {
			// 初始化和发布webApplicationContext
			this.webApplicationContext = initWebApplicationContext();

			// 子类实现
			initFrameworkServlet();

		} catch (ServletException ex) {
			this.logger.error("Context initialization failed", ex);
			throw ex;
		} catch (RuntimeException ex) {
			this.logger.error("Context initialization failed", ex);
			throw ex;
		}

		if (this.logger.isInfoEnabled()) {
			long elapsedTime = System.currentTimeMillis() - startTime;
			this.logger.info("FrameworkServlet '" + getServletName() + "': initialization completed in " +
					elapsedTime + " ms");
		}
	}

	/**
	 * 初始化和发布该servlet的WebApplicationContext
	 *
	 * 代表createWebApplicationContext()方法作为实际的WebApplicationContext创建方法，可被子类重写
	 *
	 */
	protected WebApplicationContext initWebApplicationContext() {

		// 获取root context
		WebApplicationContext rootContext = WebApplicationContextUtils.getWebApplicationContext(getServletContext());

		WebApplicationContext wac = null;

		// webApplicationContext若被注入了，则直接使用
		if (this.webApplicationContext != null) {
			wac = this.webApplicationContext;
			if (wac instanceof ConfigurableWebApplicationContext) {
				ConfigurableWebApplicationContext cwac = (ConfigurableWebApplicationContext) wac;

				// 该context还未refresh
				if (!cwac.isActive()) {
					// 父上下文为空，则将root上下文作为其父上下文
					if (cwac.getParent() == null) {
						cwac.setParent(rootContext);
					}

					// 配置和refresh WebApplicationContext对象
					configureAndRefreshWebApplicationContext(cwac);
				}
			}
		}
		if (wac == null) {
			// 在构建时没有注入上下文实例。则查看是否已在servlet上下文中注册。如果存在，就假定父上下文（如果有的话）已经被设置，并且用户执行了任何初始化，例如设置上下文id。
			wac = findWebApplicationContext();
		}
		if (wac == null) {
			// 没有为这个servlet定义上下文实例-创建一个本地实例
			wac = createWebApplicationContext(rootContext);
		}

		// 若上下文未被刷新过，则刷新一下
		if (!this.refreshEventReceived) {
			onRefresh(wac);
		}

		// 如果要公布上下文，则进行公布（就是将上下文设为servlet上下文的一个attribute属性）
		if (this.publishContext) {
			String attrName = getServletContextAttributeName();
			getServletContext().setAttribute(attrName, wac);
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Published WebApplicationContext of servlet '" + getServletName() +
						"' as ServletContext attribute with name [" + attrName + "]");
			}
		}

		return wac;
	}

	/**
	 * 取出当前servlet的attribute中WebApplicationContext实例对象
	 */
	@Nullable
	protected WebApplicationContext findWebApplicationContext() {
		// 获取当前servlet的attribute属性中WebApplicationContext对象的attrName
		String attrName = getContextAttribute();
		if (attrName == null) {
			return null;
		}

		// 从当前servlet中取出WebApplicationContext实例对象
		WebApplicationContext wac = WebApplicationContextUtils.getWebApplicationContext(getServletContext(), attrName);
		if (wac == null) {
			throw new IllegalStateException("No WebApplicationContext found: initializer not registered?");
		}
		return wac;
	}

	/**
	 * 为当前servlet创建一个WebApplicationContext实例对象
	 */
	protected WebApplicationContext createWebApplicationContext(@Nullable ApplicationContext parent) {

		Class<?> contextClass = getContextClass();

		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Servlet with name '" + getServletName() +
					"' will try to create custom WebApplicationContext context of class '" +
					contextClass.getName() + "'" + ", using parent context [" + parent + "]");
		}

		if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
			throw new ApplicationContextException(
					"Fatal initialization error in servlet with name '" + getServletName() +
					"': custom WebApplicationContext class [" + contextClass.getName() +
					"] is not of type ConfigurableWebApplicationContext");
		}

		ConfigurableWebApplicationContext wac =
				(ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);

		wac.setEnvironment(getEnvironment());
		wac.setParent(parent);
		String configLocation = getContextConfigLocation();
		if (configLocation != null) {
			wac.setConfigLocation(configLocation);
		}

		// 配置和refresh WebApplicationContext对象
		configureAndRefreshWebApplicationContext(wac);

		return wac;
	}

	/**
	 * 配置和refresh WebApplicationContext对象
 	 */
	protected void configureAndRefreshWebApplicationContext(ConfigurableWebApplicationContext wac) {
		if (ObjectUtils.identityToString(wac).equals(wac.getId())) {
			if (this.contextId != null) {
				wac.setId(this.contextId);
			} else {
				wac.setId(ConfigurableWebApplicationContext.APPLICATION_CONTEXT_ID_PREFIX +
						ObjectUtils.getDisplayString(getServletContext().getContextPath()) + '/' + getServletName());
			}
		}

		wac.setServletContext(getServletContext());
		wac.setServletConfig(getServletConfig());
		wac.setNamespace(getNamespace());
		wac.addApplicationListener(new SourceFilteringListener(wac, new ContextRefreshListener()));

		// wac environment的initPropertySources()方法都会被调用
		// 先做这个是为了确保servlet的属性来源在刷新之前出现的任何后处理或初始化时是可用的
		ConfigurableEnvironment env = wac.getEnvironment();
		if (env instanceof ConfigurableWebEnvironment) {
			((ConfigurableWebEnvironment) env).initPropertySources(getServletContext(), getServletConfig());
		}

		// 在WebApplicationContext refresh和激活成为该servlet上下文之前，后置处理该WebApplicationContext（子类实现）
		postProcessWebApplicationContext(wac);

		// 在WebApplicationContext refresh之前将其委托给该servlet初始化配置参数中"contextInitializerClasses"指定的ApplicationContextInitializer实例
		applyInitializers(wac);

		// 加载或刷新配置的持久化设置
		wac.refresh();
	}


	protected WebApplicationContext createWebApplicationContext(@Nullable WebApplicationContext parent) {
		return createWebApplicationContext((ApplicationContext) parent);
	}

	/**
	 * 在WebApplicationContext refresh和激活成为该servlet上下文之前，后置处理该WebApplicationContext
	 */
	protected void postProcessWebApplicationContext(ConfigurableWebApplicationContext wac) {
	}

	/**
	 * 在WebApplicationContext refresh之前将其委托给该servlet初始化配置参数中"contextInitializerClasses"指定的ApplicationContextInitializer实例
	 */
	protected void applyInitializers(ConfigurableApplicationContext wac) {

		// 全局配置
		String globalClassNames = getServletContext().getInitParameter(ContextLoader.GLOBAL_INITIALIZER_CLASSES_PARAM);
		if (globalClassNames != null) {
			for (String className : StringUtils.tokenizeToStringArray(globalClassNames, INIT_PARAM_DELIMITERS)) {

				// 根据class名称通过反射机制获取其实例，然后和wac绑定，并装入contextInitializers属性
				this.contextInitializers.add(loadInitializer(className, wac));
			}
		}

		// servelt的局部配置
		if (this.contextInitializerClasses != null) {
			for (String className : StringUtils.tokenizeToStringArray(this.contextInitializerClasses, INIT_PARAM_DELIMITERS)) {

				// 根据class名称通过反射机制获取其实例，然后和wac绑定，并装入contextInitializers属性
				this.contextInitializers.add(loadInitializer(className, wac));
			}
		}

		// 排序
		AnnotationAwareOrderComparator.sort(this.contextInitializers);

		// ApplicationContextInitializer对象初始化
		for (ApplicationContextInitializer<ConfigurableApplicationContext> initializer : this.contextInitializers) {
			initializer.initialize(wac);
		}
	}


	/**
	 * 根据class名称通过反射机制获取其实例，然后和wac绑定
	 */
	@SuppressWarnings("unchecked")
	private ApplicationContextInitializer<ConfigurableApplicationContext> loadInitializer(
			String className, ConfigurableApplicationContext wac) {
		try {
			Class<?> initializerClass = ClassUtils.forName(className, wac.getClassLoader());
			Class<?> initializerContextClass =
					GenericTypeResolver.resolveTypeArgument(initializerClass, ApplicationContextInitializer.class);
			if (initializerContextClass != null && !initializerContextClass.isInstance(wac)) {
				throw new ApplicationContextException(String.format(
						"Could not apply context initializer [%s] since its generic parameter [%s] " +
						"is not assignable from the type of application context used by this " +
						"framework servlet: [%s]", initializerClass.getName(), initializerContextClass.getName(),
						wac.getClass().getName()));
			}
			return BeanUtils.instantiateClass(initializerClass, ApplicationContextInitializer.class);
		} catch (ClassNotFoundException ex) {
			throw new ApplicationContextException(String.format("Could not load class [%s] specified " +
					"via 'contextInitializerClasses' init-param", className), ex);
		}
	}




	/**
	 * 该方法将在bean的所有属性已设置完毕，WebApplicationContext已经加载之后调用，默认实现是空的，子类可能重写此方法来执行它们所需的任何初始化
	 */
	protected void initFrameworkServlet() throws ServletException {
	}

	/**
	 * 刷新servlet的应用程序上下文，以及servlet的依赖状态
	 */
	public void refresh() {
		WebApplicationContext wac = getWebApplicationContext();

		if (!(wac instanceof ConfigurableApplicationContext)) {
			throw new IllegalStateException("WebApplicationContext does not support refresh: " + wac);
		}

		((ConfigurableApplicationContext) wac).refresh();

	}

	/**
	 * Callback that receives refresh events from this servlet's WebApplicationContext.
	 * <p>The default implementation calls {@link #onRefresh},
	 * triggering a refresh of this servlet's context-dependent state.
	 * @param event the incoming ApplicationContext event
	 */
	public void onApplicationEvent(ContextRefreshedEvent event) {
		this.refreshEventReceived = true;
		onRefresh(event.getApplicationContext());
	}

	/**
	 * Template method which can be overridden to add servlet-specific refresh work.
	 * Called after successful context refresh.
	 * <p>This implementation is empty.
	 * @param context the current WebApplicationContext
	 * @see #refresh()
	 */
	protected void onRefresh(ApplicationContext context) {
		// For subclasses: do nothing by default.
	}

	/**
	 * Close the WebApplicationContext of this servlet.
	 * @see org.springframework.context.ConfigurableApplicationContext#close()
	 */
	@Override
	public void destroy() {
		getServletContext().log("Destroying Spring FrameworkServlet '" + getServletName() + "'");
		// Only call close() on WebApplicationContext if locally managed...
		if (this.webApplicationContext instanceof ConfigurableApplicationContext && !this.webApplicationContextInjected) {
			((ConfigurableApplicationContext) this.webApplicationContext).close();
		}
	}


	/**
	 * Override the parent class implementation in order to intercept PATCH requests.
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		HttpMethod httpMethod = HttpMethod.resolve(request.getMethod());
		if (HttpMethod.PATCH == httpMethod || httpMethod == null) {
			processRequest(request, response);
		}
		else {
			super.service(request, response);
		}
	}

	/**
	 * Delegate GET requests to processRequest/doService.
	 * <p>Will also be invoked by HttpServlet's default implementation of {@code doHead},
	 * with a {@code NoBodyResponse} that just captures the content length.
	 * @see #doService
	 * @see #doHead
	 */
	@Override
	protected final void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate POST requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate PUT requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doPut(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate DELETE requests to {@link #processRequest}.
	 * @see #doService
	 */
	@Override
	protected final void doDelete(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		processRequest(request, response);
	}

	/**
	 * Delegate OPTIONS requests to {@link #processRequest}, if desired.
	 * <p>Applies HttpServlet's standard OPTIONS processing otherwise,
	 * and also if there is still no 'Allow' header set after dispatching.
	 * @see #doService
	 */
	@Override
	protected void doOptions(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		if (this.dispatchOptionsRequest || CorsUtils.isPreFlightRequest(request)) {
			processRequest(request, response);
			if (response.containsHeader("Allow")) {
				// Proper OPTIONS response coming from a handler - we're done.
				return;
			}
		}

		// Use response wrapper in order to always add PATCH to the allowed methods
		super.doOptions(request, new HttpServletResponseWrapper(response) {
			@Override
			public void setHeader(String name, String value) {
				if ("Allow".equals(name)) {
					value = (StringUtils.hasLength(value) ? value + ", " : "") + HttpMethod.PATCH.name();
				}
				super.setHeader(name, value);
			}
		});
	}

	/**
	 * Delegate TRACE requests to {@link #processRequest}, if desired.
	 * <p>Applies HttpServlet's standard TRACE processing otherwise.
	 * @see #doService
	 */
	@Override
	protected void doTrace(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		if (this.dispatchTraceRequest) {
			processRequest(request, response);
			if ("message/http".equals(response.getContentType())) {
				// Proper TRACE response coming from a handler - we're done.
				return;
			}
		}
		super.doTrace(request, response);
	}

	/**
	 * Process this request, publishing an event regardless of the outcome.
	 * <p>The actual event handling is performed by the abstract
	 * {@link #doService} template method.
	 */
	protected final void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		long startTime = System.currentTimeMillis();
		Throwable failureCause = null;

		LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
		LocaleContext localeContext = buildLocaleContext(request);

		RequestAttributes previousAttributes = RequestContextHolder.getRequestAttributes();
		ServletRequestAttributes requestAttributes = buildRequestAttributes(request, response, previousAttributes);

		WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
		asyncManager.registerCallableInterceptor(FrameworkServlet.class.getName(), new RequestBindingInterceptor());

		initContextHolders(request, localeContext, requestAttributes);

		try {
			doService(request, response);
		}
		catch (ServletException | IOException ex) {
			failureCause = ex;
			throw ex;
		}
		catch (Throwable ex) {
			failureCause = ex;
			throw new NestedServletException("Request processing failed", ex);
		}

		finally {
			resetContextHolders(request, previousLocaleContext, previousAttributes);
			if (requestAttributes != null) {
				requestAttributes.requestCompleted();
			}

			if (logger.isDebugEnabled()) {
				if (failureCause != null) {
					this.logger.debug("Could not complete request", failureCause);
				}
				else {
					if (asyncManager.isConcurrentHandlingStarted()) {
						logger.debug("Leaving response open for concurrent processing");
					}
					else {
						this.logger.debug("Successfully completed request");
					}
				}
			}

			publishRequestHandledEvent(request, response, startTime, failureCause);
		}
	}

	/**
	 * Build a LocaleContext for the given request, exposing the request's
	 * primary locale as current locale.
	 * @param request current HTTP request
	 * @return the corresponding LocaleContext, or {@code null} if none to bind
	 * @see LocaleContextHolder#setLocaleContext
	 */
	@Nullable
	protected LocaleContext buildLocaleContext(HttpServletRequest request) {
		return new SimpleLocaleContext(request.getLocale());
	}

	/**
	 * Build ServletRequestAttributes for the given request (potentially also
	 * holding a reference to the response), taking pre-bound attributes
	 * (and their type) into consideration.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param previousAttributes pre-bound RequestAttributes instance, if any
	 * @return the ServletRequestAttributes to bind, or {@code null} to preserve
	 * the previously bound instance (or not binding any, if none bound before)
	 * @see RequestContextHolder#setRequestAttributes
	 */
	@Nullable
	protected ServletRequestAttributes buildRequestAttributes(HttpServletRequest request,
			@Nullable HttpServletResponse response, @Nullable RequestAttributes previousAttributes) {

		if (previousAttributes == null || previousAttributes instanceof ServletRequestAttributes) {
			return new ServletRequestAttributes(request, response);
		}
		else {
			return null;  // preserve the pre-bound RequestAttributes instance
		}
	}

	private void initContextHolders(HttpServletRequest request,
			@Nullable LocaleContext localeContext, @Nullable RequestAttributes requestAttributes) {

		if (localeContext != null) {
			LocaleContextHolder.setLocaleContext(localeContext, this.threadContextInheritable);
		}
		if (requestAttributes != null) {
			RequestContextHolder.setRequestAttributes(requestAttributes, this.threadContextInheritable);
		}
		if (logger.isTraceEnabled()) {
			logger.trace("Bound request context to thread: " + request);
		}
	}

	private void resetContextHolders(HttpServletRequest request,
			@Nullable LocaleContext prevLocaleContext, @Nullable RequestAttributes previousAttributes) {

		LocaleContextHolder.setLocaleContext(prevLocaleContext, this.threadContextInheritable);
		RequestContextHolder.setRequestAttributes(previousAttributes, this.threadContextInheritable);
		if (logger.isTraceEnabled()) {
			logger.trace("Cleared thread-bound request context: " + request);
		}
	}

	private void publishRequestHandledEvent(HttpServletRequest request, HttpServletResponse response,
			long startTime, @Nullable Throwable failureCause) {

		if (this.publishEvents) {
			// Whether or not we succeeded, publish an event.
			long processingTime = System.currentTimeMillis() - startTime;
			this.webApplicationContext.publishEvent(
					new ServletRequestHandledEvent(this,
							request.getRequestURI(), request.getRemoteAddr(),
							request.getMethod(), getServletConfig().getServletName(),
							WebUtils.getSessionId(request), getUsernameForRequest(request),
							processingTime, failureCause, response.getStatus()));
		}
	}

	/**
	 * Determine the username for the given request.
	 * <p>The default implementation takes the name of the UserPrincipal, if any.
	 * Can be overridden in subclasses.
	 * @param request current HTTP request
	 * @return the username, or {@code null} if none found
	 * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
	 */
	@Nullable
	protected String getUsernameForRequest(HttpServletRequest request) {
		Principal userPrincipal = request.getUserPrincipal();
		return (userPrincipal != null ? userPrincipal.getName() : null);
	}


	/**
	 * Subclasses must implement this method to do the work of request handling,
	 * receiving a centralized callback for GET, POST, PUT and DELETE.
	 * <p>The contract is essentially the same as that for the commonly overridden
	 * {@code doGet} or {@code doPost} methods of HttpServlet.
	 * <p>This class intercepts calls to ensure that exception handling and
	 * event publication takes place.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @throws Exception in case of any kind of processing failure
	 * @see javax.servlet.http.HttpServlet#doGet
	 * @see javax.servlet.http.HttpServlet#doPost
	 */
	protected abstract void doService(HttpServletRequest request, HttpServletResponse response)
			throws Exception;


	/**
	 * ApplicationListener endpoint that receives events from this servlet's WebApplicationContext
	 * only, delegating to {@code onApplicationEvent} on the FrameworkServlet instance.
	 */
	private class ContextRefreshListener implements ApplicationListener<ContextRefreshedEvent> {

		@Override
		public void onApplicationEvent(ContextRefreshedEvent event) {
			FrameworkServlet.this.onApplicationEvent(event);
		}
	}


	/**
	 * CallableProcessingInterceptor implementation that initializes and resets
	 * FrameworkServlet's context holders, i.e. LocaleContextHolder and RequestContextHolder.
	 */
	private class RequestBindingInterceptor extends CallableProcessingInterceptorAdapter {

		@Override
		public <T> void preProcess(NativeWebRequest webRequest, Callable<T> task) {
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				HttpServletResponse response = webRequest.getNativeRequest(HttpServletResponse.class);
				initContextHolders(request, buildLocaleContext(request),
						buildRequestAttributes(request, response, null));
			}
		}
		@Override
		public <T> void postProcess(NativeWebRequest webRequest, Callable<T> task, Object concurrentResult) {
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				resetContextHolders(request, null, null);
			}
		}
	}


	public void setContextAttribute(String contextAttribute) {
		this.contextAttribute = contextAttribute;
	}

	@Nullable
	public String getContextAttribute() {
		return this.contextAttribute;
	}

	public void setContextClass(Class<?> contextClass) {
		this.contextClass = contextClass;
	}

	public Class<?> getContextClass() {
		return this.contextClass;
	}

	public void setContextId(String contextId) {
		this.contextId = contextId;
	}

	@Nullable
	public String getContextId() {
		return this.contextId;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getNamespace() {
		return (this.namespace != null ? this.namespace : getServletName() + DEFAULT_NAMESPACE_SUFFIX);
	}

	public void setContextConfigLocation(String contextConfigLocation) {
		this.contextConfigLocation = contextConfigLocation;
	}

	@Nullable
	public String getContextConfigLocation() {
		return this.contextConfigLocation;
	}

	@SuppressWarnings("unchecked")
	public void setContextInitializers(@Nullable ApplicationContextInitializer<?>... initializers) {
		if (initializers != null) {
			for (ApplicationContextInitializer<?> initializer : initializers) {
				this.contextInitializers.add((ApplicationContextInitializer<ConfigurableApplicationContext>) initializer);
			}
		}
	}

	public void setContextInitializerClasses(String contextInitializerClasses) {
		this.contextInitializerClasses = contextInitializerClasses;
	}

	public void setPublishContext(boolean publishContext) {
		this.publishContext = publishContext;
	}

	public void setPublishEvents(boolean publishEvents) {
		this.publishEvents = publishEvents;
	}

	public void setThreadContextInheritable(boolean threadContextInheritable) {
		this.threadContextInheritable = threadContextInheritable;
	}

	public void setDispatchOptionsRequest(boolean dispatchOptionsRequest) {
		this.dispatchOptionsRequest = dispatchOptionsRequest;
	}

	public void setDispatchTraceRequest(boolean dispatchTraceRequest) {
		this.dispatchTraceRequest = dispatchTraceRequest;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		if (this.webApplicationContext == null && applicationContext instanceof WebApplicationContext) {
			this.webApplicationContext = (WebApplicationContext) applicationContext;
			this.webApplicationContextInjected = true;
		}
	}

	public String getServletContextAttributeName() {
		return SERVLET_CONTEXT_PREFIX + getServletName();
	}

	public final WebApplicationContext getWebApplicationContext() {
		return this.webApplicationContext;
	}
}
