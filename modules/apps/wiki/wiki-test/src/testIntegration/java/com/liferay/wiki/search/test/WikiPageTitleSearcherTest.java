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

package com.liferay.wiki.search.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.Hits;
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.test.IdempotentRetryAssert;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.rule.Sync;
import com.liferay.portal.kernel.test.rule.SynchronousDestinationTestRule;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.SearchContextTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.security.permission.SimplePermissionChecker;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.wiki.model.WikiNode;
import com.liferay.wiki.search.WikiPageTitleSearcher;
import com.liferay.wiki.util.test.WikiTestUtil;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Roberto Díaz
 */
@RunWith(Arquillian.class)
@Sync
public class WikiPageTitleSearcherTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			SynchronousDestinationTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		setUpPermissionThreadLocal();
		setUpPrincipalThreadLocal();

		_group = GroupTestUtil.addGroup();
		_user = UserTestUtil.addUser();
		_node = WikiTestUtil.addNode(_group.getGroupId());

		_searchContext = getSearchContext(_group);
	}

	@After
	public void tearDown() {
		PermissionThreadLocal.setPermissionChecker(_originalPermissionChecker);

		PrincipalThreadLocal.setName(_originalName);
	}

	@Test
	public void testBasicSearchWithOneTerm() throws Exception {
		addPage("Barcelona", RandomTestUtil.randomString());
		addPage("Gijon", RandomTestUtil.randomString());
		addPage("Gijon (Asturias)", RandomTestUtil.randomString());
		addPage("Madrid", RandomTestUtil.randomString());

		assertSearch("Asturias", 1);
		assertSearch("Gijon", 2);
		assertSearch("Madrid", 1);
	}

	@Test
	public void testBasicSearchWithOneTermOnlyByTitle() throws Exception {
		addPage("Barcelona", "Spanish city");
		addPage("Madrid", "Spanish city");

		assertSearch("city", 0);
	}

	@Test
	public void testLikeSearchWithOneTerm() throws Exception {
		addPage("Gejon", RandomTestUtil.randomString());
		addPage("Gijom", RandomTestUtil.randomString());
		addPage("Gijon", RandomTestUtil.randomString());
		addPage("Gijun", RandomTestUtil.randomString());
		addPage("Gikon", RandomTestUtil.randomString());

		assertSearch("G", 5);
		assertSearch("Gi", 4);
		assertSearch("Gij", 3);
		assertSearch("Gijo", 2);
		assertSearch("Gijon", 1);
	}

	protected void assertSearch(final String keywords, final int length)
		throws Exception {

		IdempotentRetryAssert.retryAssert(
			3, TimeUnit.SECONDS,
			new Callable<Void>() {

				@Override
				public Void call() throws Exception {
					_searchContext.setKeywords(
						StringUtil.toLowerCase(keywords));

					Indexer<?> indexer = WikiPageTitleSearcher.getInstance();

					Hits hits = indexer.search(_searchContext);

					Assert.assertEquals(length, hits.getLength());

					return null;
				}

			});
	}

	protected SearchContext getSearchContext(Group group) throws Exception {
		SearchContext searchContext = SearchContextTestUtil.getSearchContext(
			group.getGroupId());

		return searchContext;
	}

	protected void setUpPermissionThreadLocal() throws Exception {
		_originalPermissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		PermissionThreadLocal.setPermissionChecker(
			new SimplePermissionChecker() {

				@Override
				public boolean hasOwnerPermission(
					long companyId, String name, String primKey, long ownerId,
					String actionId) {

					return true;
				}

			});
	}

	protected void setUpPrincipalThreadLocal() throws Exception {
		_originalName = PrincipalThreadLocal.getName();

		PrincipalThreadLocal.setName(TestPropsValues.getUserId());
	}

	private void addPage(String title, String content) throws Exception {
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), _user.getUserId());

		WikiTestUtil.addPage(
			_user.getUserId(), _node.getNodeId(), title, content, true,
			serviceContext);
	}

	@DeleteAfterTestRun
	private Group _group;

	private WikiNode _node;
	private String _originalName;
	private PermissionChecker _originalPermissionChecker;
	private SearchContext _searchContext;

	@DeleteAfterTestRun
	private User _user;

}