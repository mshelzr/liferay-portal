/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.kernel.servlet;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.CharPool;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;

import java.io.IOException;
import java.io.InputStream;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.servlet.ServletContext;

/**
 * @author Brian Wing Shun Chan
 * @author Raymond Augé
 * @author James Lefeu
 */
public class ServletContextUtil {

	public static final String PATH_WEB_INF = "/WEB-INF";

	public static final String URI_ATTRIBUTE =
		ServletContextUtil.class.getName().concat(".rootURI");

	public static Set<String> getClassNames(ServletContext servletContext)
		throws IOException {

		Set<String> classNames = new HashSet<String>();

		_getClassNames(servletContext, "/WEB-INF/classes", classNames);
		_getClassNames(servletContext, "/WEB-INF/lib", classNames);

		return classNames;
	}

	public static long getLastModified(ServletContext servletContext) {
		return getLastModified(servletContext, StringPool.SLASH);
	}

	public static long getLastModified(
		ServletContext servletContext, String path) {

		return getLastModified(servletContext, path, false);
	}

	public static long getLastModified(
		ServletContext servletContext, String path, boolean cache) {

		String lastModifiedCacheKey = null;

		if (cache) {
			lastModifiedCacheKey = ServletContextUtil.class.getName();
			lastModifiedCacheKey = lastModifiedCacheKey.concat(
				StringPool.PERIOD).concat(path);

			Long lastModified = (Long)servletContext.getAttribute(
				lastModifiedCacheKey);

			if (lastModified != null) {
				return lastModified.longValue();
			}
		}

		String curPath = null;

		long lastModified = 0;

		Queue<String> pathQueue = new LinkedList<String>();

		pathQueue.offer(path);

		while ((curPath = pathQueue.poll()) != null) {
			if (curPath.charAt(curPath.length() - 1) == CharPool.SLASH) {
				Set<String> pathSet = servletContext.getResourcePaths(curPath);

				if (pathSet != null) {
					pathQueue.addAll(pathSet);
				}
			}
			else {
				try {
					URL url = servletContext.getResource(curPath);

					if (url == null) {
						_log.error("Resource URL for " + curPath + " is null");
					}
					else {
						URLConnection urlConnection = null;

						try {
							urlConnection = url.openConnection();

							if (urlConnection.getLastModified() >
									lastModified) {

								lastModified = urlConnection.getLastModified();
							}
						}
						finally {
							if (urlConnection != null) {
								try {
									InputStream inputStream =
										urlConnection.getInputStream();

									inputStream.close();
								}
								catch (IOException ioe) {
								}
							}
						}
					}
				}
				catch (IOException ioe) {
					_log.error(ioe, ioe);
				}
			}
		}

		if (cache) {
			servletContext.setAttribute(
				lastModifiedCacheKey, new Long(lastModified));
		}

		return lastModified;
	}

	public static String getResourcePath(URL url) throws URISyntaxException {
		URI uri = getResourceURI(url);

		return uri.toString();
	}

	public static URI getResourceURI(URL url) throws URISyntaxException {
		return _getResourceURI(url, url.getPath());
	}

	public static String getRootPath(ServletContext servletContext)
		throws MalformedURLException {

		URI rootURI = getRootURI(servletContext);

		return rootURI.toString();
	}

	public static URI getRootURI(ServletContext servletContext)
		throws MalformedURLException {

		URI rootURI = (URI)servletContext.getAttribute(URI_ATTRIBUTE);

		if (rootURI != null) {
			return rootURI;
		}

		try {
			URL rootURL = servletContext.getResource(PATH_WEB_INF);

			String path = rootURL.getPath();

			int index = path.indexOf(PATH_WEB_INF);

			if (index < 0) {
				throw new MalformedURLException("Invalid URL " + rootURL);
			}

			if (index == 0) {
				path = StringPool.SLASH;
			}
			else {
				path = path.substring(0, index);
			}

			rootURI = _getResourceURI(rootURL, path);

			servletContext.setAttribute(URI_ATTRIBUTE, rootURI);
		}
		catch (URISyntaxException urise) {
			throw new MalformedURLException(urise.getMessage());
		}

		return rootURI;
	}

	private static String _getClassName(String path) {
		return _getClassName(null, path);
	}

	private static String _getClassName(String rootPath, String path) {
		String className = path.substring(
			0, path.length() - _EXT_CLASS.length());

		if (rootPath != null) {
			className = className.substring(rootPath.length() + 1);
		}

		className = StringUtil.replace(
			className, CharPool.SLASH, CharPool.PERIOD);

		return className;
	}

	private static void _getClassNames(
			ServletContext servletContext, String rootPath,
			Set<String> classNames)
		throws IOException {

		_getClassNames(
			servletContext, rootPath, servletContext.getResourcePaths(rootPath),
			classNames);
	}

	private static void _getClassNames(
			ServletContext servletContext, String rootPath, Set<String> paths,
			Set<String> classNames)
		throws IOException {

		if (paths == null) {
			return;
		}

		for (String path : paths) {
			if (path.endsWith(_EXT_CLASS)) {
				String className = _getClassName(rootPath, path);

				classNames.add(className);
			}
			else if (path.endsWith(_EXT_JAR)) {
				JarInputStream jarFile = new JarInputStream(
					servletContext.getResourceAsStream(path));

				while (true) {
					JarEntry jarEntry = jarFile.getNextJarEntry();

					if (jarEntry == null) {
						break;
					}

					String jarEntryName = jarEntry.getName();

					if (jarEntryName.endsWith(_EXT_CLASS)) {
						String className = _getClassName(jarEntryName);

						classNames.add(className);
					}
				}

				jarFile.close();
			}
			else if (path.endsWith(StringPool.SLASH)) {
				_getClassNames(
					servletContext, rootPath,
					servletContext.getResourcePaths(path), classNames);
			}
		}
	}

	private static URI _getResourceURI(URL url, String path)
		throws URISyntaxException {

		return new URI(url.getProtocol(), path, null);
	}

	private static final String _EXT_CLASS = ".class";

	private static final String _EXT_JAR = ".jar";

	private static Log _log = LogFactoryUtil.getLog(ServletContextUtil.class);

}