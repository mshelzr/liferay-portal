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

package com.liferay.portlet.wiki.action;

import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.struts.PortletAction;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.WebKeys;
import com.liferay.portlet.wiki.NoSuchPageException;
import com.liferay.portlet.wiki.model.WikiNode;
import com.liferay.portlet.wiki.model.WikiPage;
import com.liferay.portlet.wiki.service.WikiPageServiceUtil;
import com.liferay.portlet.wiki.util.WikiUtil;

import javax.portlet.PortletConfig;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/**
 * @author Bruno Farache
 * @author Julio Camarero
 */
public class CompareVersionsAction extends PortletAction {

	@Override
	public ActionForward render(
			ActionMapping actionMapping, ActionForm actionForm,
			PortletConfig portletConfig, RenderRequest renderRequest,
			RenderResponse renderResponse)
		throws Exception {

		try {
			ActionUtil.getNode(renderRequest);
			ActionUtil.getPage(renderRequest);

			compareVersions(renderRequest, renderResponse);
		}
		catch (Exception e) {
			if (e instanceof NoSuchPageException) {
				SessionErrors.add(renderRequest, e.getClass());

				return actionMapping.findForward("portlet.wiki.error");
			}
			else {
				throw e;
			}
		}

		return actionMapping.findForward("portlet.wiki.compare_versions");
	}

	protected void compareVersions(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		long nodeId = ParamUtil.getLong(renderRequest, "nodeId");
		String title = ParamUtil.getString(renderRequest, "title");
		double sourceVersion = ParamUtil.getDouble(
			renderRequest, "sourceVersion");
		double targetVersion = ParamUtil.getDouble(
			renderRequest, "targetVersion");

		WikiPage sourcePage = WikiPageServiceUtil.getPage(
			nodeId, title, sourceVersion);
		WikiPage targetPage = WikiPageServiceUtil.getPage(
			nodeId, title, targetVersion);

		PortletURL viewPageURL = renderResponse.createRenderURL();

		viewPageURL.setParameter("struts_action", "/wiki/view");

		WikiNode sourceNode = sourcePage.getNode();

		viewPageURL.setParameter("nodeName", sourceNode.getName());

		PortletURL editPageURL = renderResponse.createRenderURL();

		editPageURL.setParameter("struts_action", "/wiki/edit_page");
		editPageURL.setParameter("nodeId", String.valueOf(nodeId));
		editPageURL.setParameter("title", title);

		String attachmentURLPrefix = WikiUtil.getAttachmentURLPrefix(
			themeDisplay.getPathMain(), themeDisplay.getPlid(), nodeId, title);

		String htmlDiffResult = WikiUtil.diffHtml(
			sourcePage, targetPage, viewPageURL, editPageURL,
			attachmentURLPrefix);

		renderRequest.setAttribute(WebKeys.DIFF_HTML_RESULTS, htmlDiffResult);
		renderRequest.setAttribute(WebKeys.SOURCE_VERSION, sourceVersion);
		renderRequest.setAttribute(WebKeys.TARGET_VERSION, targetVersion);
		renderRequest.setAttribute(WebKeys.TITLE, title);
		renderRequest.setAttribute(WebKeys.WIKI_NODE_ID, nodeId);
	}

}