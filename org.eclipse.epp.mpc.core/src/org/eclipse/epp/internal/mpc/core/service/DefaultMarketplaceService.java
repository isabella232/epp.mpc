/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      The Eclipse Foundation - initial API and implementation
 *      Yatta Solutions - bug 397004, bug 385936, bug 432803: public API
 *******************************************************************************/
package org.eclipse.epp.internal.mpc.core.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.epp.internal.mpc.core.MarketplaceClientCore;
import org.eclipse.epp.internal.mpc.core.model.Category;
import org.eclipse.epp.internal.mpc.core.model.Market;
import org.eclipse.epp.internal.mpc.core.model.Marketplace;
import org.eclipse.epp.internal.mpc.core.model.News;
import org.eclipse.epp.internal.mpc.core.model.Node;
import org.eclipse.epp.internal.mpc.core.model.NodeListing;
import org.eclipse.epp.internal.mpc.core.model.Search;
import org.eclipse.epp.internal.mpc.core.model.SearchResult;
import org.eclipse.epp.internal.mpc.core.service.AbstractDataStorageService.NotAuthorizedException;
import org.eclipse.epp.internal.mpc.core.util.HttpUtil;
import org.eclipse.epp.internal.mpc.core.util.ServiceUtil;
import org.eclipse.epp.internal.mpc.core.util.URLUtil;
import org.eclipse.epp.mpc.core.model.ICategory;
import org.eclipse.epp.mpc.core.model.IIdentifiable;
import org.eclipse.epp.mpc.core.model.IIus;
import org.eclipse.epp.mpc.core.model.IMarket;
import org.eclipse.epp.mpc.core.model.INode;
import org.eclipse.epp.mpc.core.model.ISearchResult;
import org.eclipse.epp.mpc.core.service.IMarketplaceService;
import org.eclipse.epp.mpc.core.service.IMarketplaceServiceLocator;
import org.eclipse.epp.mpc.core.service.IUserFavoritesService;
import org.eclipse.epp.mpc.core.service.ServiceHelper;
import org.eclipse.osgi.util.NLS;

/**
 * @author David Green
 * @author Carsten Reckord
 */
@SuppressWarnings("deprecation")
public class DefaultMarketplaceService extends RemoteMarketplaceService<Marketplace> implements IMarketplaceService,
MarketplaceService {

//	This provisional API will be identified by /api/p at the end of most urls.
//
//	/api/p - Returns Markets + Categories
//	/node/%/api/p OR /content/%/api/p - Returns a single listing's detail
//	/taxonomy/term/%/api/p - Returns a category listing of results
//	/featured/api/p - Returns a server-defined number of featured results.
//	/recent/api/p - Returns a server-defined number of recent updates
//	/favorites/top/api/p - Returns a server-defined number of top favorites
//	/popular/top/api/p - Returns a server-defined number of most active results
//	/related/api/p - Returns a server-defined number of recommendations based on a list of nodes provided as query parameter
//	/news/api/p - Returns the news configuration details (news location/title...).
//
//	There is one exception to adding /api/p at the end and that is for search results.
//
//	/api/p/search/apachesolr_search/[query]?page=[]&filters=[] - Returns search result from the Solr Search DB.
//
//	Once we've locked down the provisional API it will likely be named api/1.

	public static final String API_FAVORITES_URI = "favorites/top"; //$NON-NLS-1$

	public static final String API_FEATURED_URI = "featured"; //$NON-NLS-1$

	public static final String API_NEWS_URI = "news"; //$NON-NLS-1$

	public static final String API_NODE_CONTENT_URI = "content"; //$NON-NLS-1$

	public static final String API_NODE_URI = "node"; //$NON-NLS-1$

	public static final String API_POPULAR_URI = "popular/top"; //$NON-NLS-1$

	public static final String API_RELATED_URI = "related"; //$NON-NLS-1$

	public static final String API_RECENT_URI = "recent"; //$NON-NLS-1$

	public static final String API_SEARCH_URI = "search/apachesolr_search/"; //$NON-NLS-1$

	public static final String API_SEARCH_URI_FULL = API_URI_SUFFIX + '/' + API_SEARCH_URI;

	public static final String API_TAXONOMY_URI = "taxonomy/term/"; //$NON-NLS-1$

	public static final String API_FREETAGGING_URI = "category/free-tagging/"; //$NON-NLS-1$

	public static final String DEFAULT_SERVICE_LOCATION = System
			.getProperty(IMarketplaceServiceLocator.DEFAULT_MARKETPLACE_PROPERTY_NAME, "http://marketplace.eclipse.org"); //$NON-NLS-1$

	public static final URL DEFAULT_SERVICE_URL;

	/**
	 * parameter identifying client
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_CLIENT = "client"; //$NON-NLS-1$

	/**
	 * parameter identifying client plugin version
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_CLIENT_VERSION = "client.version"; //$NON-NLS-1$

	/**
	 * parameter identifying windowing system as reported by {@link org.eclipse.core.runtime.Platform#getWS()}
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_WS = "ws"; //$NON-NLS-1$

	/**
	 * parameter identifying operating system as reported by {@link org.eclipse.core.runtime.Platform#getOS()}
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_OS = "os"; //$NON-NLS-1$

	/**
	 * parameter identifying the current local as reported by {@link org.eclipse.core.runtime.Platform#getNL()}
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_NL = "nl"; //$NON-NLS-1$

	/**
	 * parameter identifying Java version
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_JAVA_VERSION = "java.version"; //$NON-NLS-1$

	/**
	 * parameter identifying the Eclipse runtime version (the version of the org.eclipse.core.runtime bundle)
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_RUNTIME_VERSION = "runtime.version"; //$NON-NLS-1$

	/**
	 * parameter identifying the Eclipse platform version (the version of the org.eclipse.platform bundle) This
	 * parameter is optional and only sent if the platform bundle is present. It is used to identify the actual running
	 * platform's version (esp. where different platforms share the same runtime, like the parallel 3.x/4.x versions)
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_PLATFORM_VERSION = "platform.version"; //$NON-NLS-1$

	/**
	 * parameter identifying the Eclipse product version
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_PRODUCT_VERSION = "product.version"; //$NON-NLS-1$

	/**
	 * parameter identifying the product id, as provided by <code>Platform.getProduct().getId()</code>
	 *
	 * @see {@link #setRequestMetaParameters(Map)}
	 */
	public static final String META_PARAM_PRODUCT = "product"; //$NON-NLS-1$

	/**
	 * parameter identifying a list of nodes for a {@link #related(List, IProgressMonitor)} query
	 */
	public static final String PARAM_BASED_ON_NODES = "nodes"; //$NON-NLS-1$

	static {
		DEFAULT_SERVICE_URL = ServiceUtil.parseUrl(DEFAULT_SERVICE_LOCATION);
	}

	private IUserFavoritesService userFavoritesService;

	public DefaultMarketplaceService(URL baseUrl) {
		this.baseUrl = baseUrl == null ? DEFAULT_SERVICE_URL : baseUrl;
	}

	public DefaultMarketplaceService() {
		this(null);
	}

	@Override
	public URL getBaseUrl() {
		return super.getBaseUrl();
	}

	public List<Market> listMarkets(IProgressMonitor monitor) throws CoreException {
		Marketplace marketplace = processRequest(API_URI_SUFFIX, monitor);
		return marketplace.getMarket();
	}

	public Market getMarket(IMarket market, IProgressMonitor monitor) throws CoreException {
		if (market.getId() == null && market.getUrl() != null) {
			throw new IllegalArgumentException();
		}
		List<Market> markets = listMarkets(monitor);
		if (market.getId() != null) {
			String marketId = market.getId();
			for (Market aMarket : markets) {
				if (marketId.equals(aMarket.getId())) {
					return aMarket;
				}
			}
		} else if (market.getUrl() != null) {
			String marketUrl = market.getUrl();
			for (Market aMarket : markets) {
				if (marketUrl.equals(aMarket.getUrl())) {
					return aMarket;
				}
			}
		}
		throw new CoreException(createErrorStatus(Messages.DefaultMarketplaceService_marketNotFound, null));
	}

	public Market getMarket(Market market, IProgressMonitor monitor) throws CoreException {
		return getMarket((IMarket) market, monitor);
	}

	public Category getCategory(ICategory category, IProgressMonitor monitor) throws CoreException {
		SubMonitor progress = SubMonitor.convert(monitor, 200);
		if (category.getId() != null && category.getUrl() == null) {
			List<Market> markets = listMarkets(progress.newChild(50));
			ICategory resolvedCategory = null;
			outer: for (Market market : markets) {
				List<Category> categories = market.getCategory();
				for (Category aCategory : categories) {
					if (aCategory.equalsId(category)) {
						resolvedCategory = aCategory;
						break outer;
					}
				}
			}
			if (progress.isCanceled()) {
				throw new OperationCanceledException();
			} else if (resolvedCategory == null) {
				throw new CoreException(createErrorStatus(Messages.DefaultMarketplaceService_categoryNotFound, null));
			} else {
				return getCategory(resolvedCategory, progress.newChild(150));
			}
		}
		Marketplace marketplace = processRequest(category.getUrl(), API_URI_SUFFIX, progress.newChild(200));
		if (marketplace.getCategory().isEmpty()) {
			throw new CoreException(createErrorStatus(Messages.DefaultMarketplaceService_categoryNotFound, null));
		} else if (marketplace.getCategory().size() > 1) {
			throw new CoreException(createErrorStatus(Messages.DefaultMarketplaceService_unexpectedResponse, null));
		}
		Category resolvedCategory = marketplace.getCategory().get(0);
		return resolvedCategory;
	}

	public Category getCategory(Category category, IProgressMonitor monitor) throws CoreException {
		return getCategory((ICategory) category, monitor);
	}

	public Node getNode(INode node, IProgressMonitor monitor) throws CoreException {
		Marketplace marketplace;
		if (node.getId() != null) {
			// bug 304928: prefer the id method rather than the URL, since the id provides a stable URL and the
			// URL is based on the name, which could change.
			String encodedId = urlEncode(node.getId());
			marketplace = processRequest(API_NODE_URI + '/' + encodedId + '/' + API_URI_SUFFIX, monitor);
		} else {
			marketplace = processRequest(node.getUrl(), API_URI_SUFFIX, monitor);
		}
		if (marketplace.getNode().isEmpty()) {
			throw new CoreException(createErrorStatus(Messages.DefaultMarketplaceService_nodeNotFound, null));
		} else if (marketplace.getNode().size() > 1) {
			throw new CoreException(createErrorStatus(Messages.DefaultMarketplaceService_unexpectedResponse, null));
		}
		Node resolvedNode = marketplace.getNode().get(0);
		return resolvedNode;
	}

	public Node getNode(Node node, IProgressMonitor monitor) throws CoreException {
		return getNode((INode) node, monitor);
	}

	public SearchResult search(IMarket market, ICategory category, String queryText, IProgressMonitor monitor)
			throws CoreException {
		String relativeUrl = computeRelativeSearchUrl(market, category, queryText, true);
		return processSearchRequest(relativeUrl, queryText, monitor);
	}

	public SearchResult search(Market market, Category category, String queryText, IProgressMonitor monitor)
			throws CoreException {
		return search((IMarket) market, (ICategory) category, queryText, monitor);
	}

	/**
	 * Creates the query URL for the Marketplace REST API.
	 * <p>
	 * If the query string is non-empty, the format for the returned relative URL is
	 * <code>search/apachesolr_search/[query]?filters=[filters]</code> where [query] is the URL encoded query string and
	 * [filters] are the category and market IDs (category first for browser urls, market first for API urls). If both
	 * market and category are null, the filters are omitted completely.
	 * <p>
	 * If the query is empty and either market or category are not null, the format for the relative URL is
	 * <code>taxonomy/term/[filters]</code> where [filters] is the comma-separated list of category and market, in that
	 * order.
	 * <p>
	 * If the query is empty and both category and market are null, the result is null
	 *
	 * @param market
	 *            the market to search or null
	 * @param category
	 *            the category to search or null
	 * @param queryText
	 *            the search query
	 * @param api
	 *            true to create REST API url, false for browser url
	 * @return the relative search url, e.g.
	 *         <code>api/p/search/apachesolr_search/WikiText?filters=tid:38%20tid:31</code> or
	 *         <code>taxonomy/term/38,31/api/p</code>
	 */
	public String computeRelativeSearchUrl(IMarket market, ICategory category, String queryText, boolean api) {
		String relativeUrl;
		if (queryText != null && queryText.trim().length() > 0) {
			relativeUrl = (api ? API_SEARCH_URI_FULL : API_SEARCH_URI) + urlEncode(queryText.trim());
			String queryString = ""; //$NON-NLS-1$
			if (market != null || category != null) {
				queryString += "filters="; //$NON-NLS-1$
				IIdentifiable first = api ? market : category;
				IIdentifiable second = api ? category : market;
				if (first != null) {
					queryString += "tid:" + urlEncode(first.getId()); //$NON-NLS-1$
					if (second != null) {
						queryString += "%20"; //$NON-NLS-1$
					}
				}
				if (second != null) {
					queryString += "tid:" + urlEncode(second.getId()); //$NON-NLS-1$
				}
			}
			if (queryString.length() > 0) {
				relativeUrl += '?' + queryString;
			}
		} else if (market != null || category != null) {
			// http://marketplace.eclipse.org/taxonomy/term/38,31
			relativeUrl = API_TAXONOMY_URI;
			if (category != null) {
				relativeUrl += urlEncode(category.getId());
				if (market != null) {
					relativeUrl += ',';
				}
			}
			if (market != null) {
				relativeUrl += urlEncode(market.getId());
			}
			if (api) {
				relativeUrl += '/' + API_URI_SUFFIX;
			}
		} else {
			relativeUrl = null;
		}
		return relativeUrl;
	}

	private SearchResult processSearchRequest(String relativeUrl, String queryText, IProgressMonitor monitor)
			throws CoreException {
		SearchResult result = new SearchResult();
		if (relativeUrl == null) {
			// empty search
			result.setMatchCount(0);
			result.setNodes(new ArrayList<Node>());
		} else {
			Marketplace marketplace;
			try {
				marketplace = processRequest(relativeUrl, monitor);
			} catch (CoreException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof FileNotFoundException) {
					throw new CoreException(createErrorStatus(
							NLS.bind(Messages.DefaultMarketplaceService_UnsupportedSearchString, queryText), cause));
				}
				throw ex;
			}
			Search search = marketplace.getSearch();
			if (search != null) {
				result.setMatchCount(search.getCount());
				result.setNodes(search.getNode());
			} else if (marketplace.getCategory().size() == 1) {
				Category category = marketplace.getCategory().get(0);
				result.setMatchCount(category.getNode().size());
				result.setNodes(category.getNode());
			} else {
				throw new CoreException(createErrorStatus(Messages.DefaultMarketplaceService_unexpectedResponse, null));
			}
		}
		return result;
	}

	public SearchResult tagged(String tag, IProgressMonitor monitor) throws CoreException {
		return processSearchRequest(API_FREETAGGING_URI + URLUtil.urlEncode(tag) + '/' + API_URI_SUFFIX, tag, monitor);
	}

	public SearchResult featured(IProgressMonitor monitor) throws CoreException {
		return featured(null, null, monitor);
	}

	public SearchResult featured(IMarket market, ICategory category, IProgressMonitor monitor) throws CoreException {
		String nodePart = ""; //$NON-NLS-1$
		if (market != null) {
			nodePart += urlEncode(market.getId());
		}
		if (category != null) {
			if (nodePart.length() > 0) {
				nodePart += ","; //$NON-NLS-1$
			}
			nodePart += urlEncode(category.getId());
		}
		String uri = API_FEATURED_URI + '/';
		if (nodePart.length() > 0) {
			uri += nodePart + '/';
		}
		Marketplace marketplace = processRequest(uri + API_URI_SUFFIX, monitor);
		return createSearchResult(marketplace.getFeatured());
	}

	public SearchResult featured(IProgressMonitor monitor, Market market, Category category) throws CoreException {
		return featured(market, category, monitor);
	}

	public SearchResult recent(IProgressMonitor monitor) throws CoreException {
		Marketplace marketplace = processRequest(API_RECENT_URI + '/' + API_URI_SUFFIX, monitor);
		return createSearchResult(marketplace.getRecent());
	}

	/**
	 * @deprecated use {@link #topFavorites(IProgressMonitor)} instead
	 */
	@Deprecated
	public SearchResult favorites(IProgressMonitor monitor) throws CoreException {
		return topFavorites(monitor);
	}

	public SearchResult topFavorites(IProgressMonitor monitor) throws CoreException {
		Marketplace marketplace = processRequest(API_FAVORITES_URI + '/' + API_URI_SUFFIX, monitor);
		return createSearchResult(marketplace.getFavorites());
	}

	public ISearchResult userFavorites(IProgressMonitor monitor) throws CoreException, NotAuthorizedException {
		SubMonitor progress = SubMonitor.convert(monitor, Messages.DefaultMarketplaceService_FavoritesRetrieve, 10000);
		IUserFavoritesService userFavoritesService = getUserFavoritesService();
		if (userFavoritesService == null) {
			throw new UnsupportedOperationException();
		}
		final List<INode> favorites;
		try {
			favorites = userFavoritesService.getFavorites(progress.newChild(1000));
		} catch (NotAuthorizedException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(MarketplaceClientCore.computeStatus(e, Messages.DefaultMarketplaceService_FavoritesErrorRetrieving));
		}
		progress.setWorkRemaining(9000);
		return resolveFavoriteNodes(favorites, progress.newChild(9000), true);
	}

	public ISearchResult userFavorites(URI favoritesUri, IProgressMonitor monitor) throws CoreException {
		SubMonitor progress = SubMonitor.convert(monitor, Messages.DefaultMarketplaceService_FavoritesRetrieve, 10000);
		IUserFavoritesService userFavoritesService = getUserFavoritesService();
		if (userFavoritesService == null) {
			throw new UnsupportedOperationException();
		}
		final List<INode> favorites;
		try {
			favorites = userFavoritesService.getFavorites(favoritesUri, progress.newChild(1000));
		} catch (Exception e) {
			throw new CoreException(MarketplaceClientCore.computeStatus(e,
					Messages.DefaultMarketplaceService_FavoritesErrorRetrieving));
		}
		progress.setWorkRemaining(9000);
		return resolveFavoriteNodes(favorites, progress.newChild(9000), false);
	}

	public void userFavorites(List<? extends INode> nodes, IProgressMonitor monitor)
			throws NotAuthorizedException, CoreException {
		SubMonitor progress = SubMonitor.convert(monitor, Messages.DefaultMarketplaceService_FavoritesUpdate, 10000);
		IUserFavoritesService userFavoritesService = getUserFavoritesService();
		if (userFavoritesService == null) {
			throw new UnsupportedOperationException();
		}
		if (nodes == null || nodes.isEmpty()) {
			return;
		}
		Set<String> favorites = null;
		try {
			favorites = userFavoritesService == null ? null : userFavoritesService.getFavoriteIds(progress);
		} catch (NotAuthorizedException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(MarketplaceClientCore.computeStatus(e, Messages.DefaultMarketplaceService_FavoritesErrorRetrieving));
		} finally {
			for (INode node : nodes) {
				((Node) node).setUserFavorite(favorites == null ? null : favorites.contains(node.getId()));
			}
		}
	}

	private ISearchResult resolveFavoriteNodes(final List<INode> nodes, IProgressMonitor monitor, boolean filterIncompatible) throws CoreException {
		IMarketplaceService resolveService = this;
		IMarketplaceService registeredService = ServiceHelper.getMarketplaceServiceLocator()
				.getMarketplaceService(this.getBaseUrl().toString());
		if (registeredService instanceof CachingMarketplaceService) {
			CachingMarketplaceService cachingService = (CachingMarketplaceService) registeredService;
			if (cachingService.getDelegate() == this) {
				resolveService = cachingService;
			}
		}
		SubMonitor resolveProgress = SubMonitor.convert(monitor, nodes.size() * 100);
		for (ListIterator<INode> i = nodes.listIterator(); i.hasNext();) {
			INode node = i.next();
			INode resolved = resolveService.getNode(node, resolveProgress.newChild(100));
			((Node) resolved).setUserFavorite(true);
			if (filterIncompatible && !isInstallable(resolved)) {
				i.remove();
			} else {
				i.set(resolved);
			}
		}
		if (!filterIncompatible) {
			//sort the node list so uninstallable nodes come last
			Collections.sort(nodes, new Comparator<INode>() {

				public int compare(INode n1, INode n2) {
					if (n1 == n2) {
						return 0;
					}
					boolean n1Installable = isInstallable(n1);
					boolean n2Installable = isInstallable(n2);
					if (n1Installable == n2Installable) {
						return 0;
					}
					if (n1Installable) { // && !n2Installable
						return -1;
					}
					// !n1Installable && n2Installable
					return 1;
				}
			});
		}

		return new ISearchResult() {

			public List<? extends INode> getNodes() {
				return nodes;
			}

			public Integer getMatchCount() {
				return nodes.size();
			}
		};
	}

	private boolean isInstallable(INode resolved) {
		IIus ius = resolved.getIus();
		return ius != null && !ius.getIuElements().isEmpty();
	}

	public SearchResult popular(IProgressMonitor monitor) throws CoreException {
		Marketplace marketplace = processRequest(API_POPULAR_URI + '/' + API_URI_SUFFIX, monitor);
		return createSearchResult(marketplace.getPopular());
	}

	public SearchResult related(List<? extends INode> basedOn, IProgressMonitor monitor) throws CoreException {
		String basedOnQuery = ""; //$NON-NLS-1$
		if (basedOn != null && !basedOn.isEmpty()) {
			StringBuilder sb = new StringBuilder().append('?').append(PARAM_BASED_ON_NODES).append('=');
			boolean first = true;
			for (INode node : basedOn) {
				if (!first) {
					sb.append('+');
				}
				sb.append(node.getId());
				first = false;
			}
			basedOnQuery = sb.toString();
		}
		Marketplace marketplace = processRequest(API_RELATED_URI + '/' + API_URI_SUFFIX + basedOnQuery, monitor);
		return createSearchResult(marketplace.getRelated());
	}

	protected SearchResult createSearchResult(NodeListing nodeList) throws CoreException {
		if (nodeList == null) {
			throw new CoreException(createErrorStatus(Messages.DefaultMarketplaceService_unexpectedResponse, null));
		}
		SearchResult result = new SearchResult();
		result.setMatchCount(nodeList.getCount());
		result.setNodes(nodeList.getNode());
		return result;
	}

	public News news(IProgressMonitor monitor) throws CoreException {
		try {
			Marketplace marketplace = processRequest(API_NEWS_URI + '/' + API_URI_SUFFIX, monitor);
			return marketplace.getNews();
		} catch (CoreException ex) {
			final Throwable cause = ex.getCause();
			if (cause instanceof FileNotFoundException) {
				// optional news API not supported
				return null;
			}
			throw ex;
		}
	}

	/**
	 * @deprecated use {@link #reportInstallError(IStatus, Set, Set, String, IProgressMonitor)} instead
	 */
	@Deprecated
	public void reportInstallError(IProgressMonitor monitor, IStatus result, Set<Node> nodes,
			Set<String> iuIdsAndVersions, String resolutionDetails) throws CoreException {
		reportInstallError(result, nodes, iuIdsAndVersions, resolutionDetails, monitor);
	}

	public void reportInstallError(IStatus result, Set<? extends INode> nodes, Set<String> iuIdsAndVersions,
			String resolutionDetails, IProgressMonitor monitor) throws CoreException {
		HttpClient client;
		URL location;
		HttpPost method;
		try {
			location = new URL(baseUrl, "install/error/report"); //$NON-NLS-1$
			String target = location.toURI().toString();
			client = HttpUtil.createHttpClient(target);
			method = new HttpPost(target);
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		} catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
		try {
			List<NameValuePair> parameters = new ArrayList<NameValuePair>();

			Map<String, String> requestMetaParameters = getRequestMetaParameters();
			for (Map.Entry<String, String> metaParam : requestMetaParameters.entrySet()) {
				if (metaParam.getKey() != null) {
					parameters.add(new BasicNameValuePair(metaParam.getKey(), metaParam.getValue()));
				}
			}

			parameters.add(new BasicNameValuePair("status", Integer.toString(result.getSeverity()))); //$NON-NLS-1$
			parameters.add(new BasicNameValuePair("statusMessage", result.getMessage())); //$NON-NLS-1$
			for (INode node : nodes) {
				parameters.add(new BasicNameValuePair("node", node.getId())); //$NON-NLS-1$
			}
			if (iuIdsAndVersions != null && !iuIdsAndVersions.isEmpty()) {
				for (String iuAndVersion : iuIdsAndVersions) {
					parameters.add(new BasicNameValuePair("iu", iuAndVersion)); //$NON-NLS-1$
				}
			}
			parameters.add(new BasicNameValuePair("detailedMessage", resolutionDetails)); //$NON-NLS-1$
			if (!parameters.isEmpty()) {
				UrlEncodedFormEntity entity = new UrlEncodedFormEntity(parameters, "UTF-8"); //$NON-NLS-1$
				method.setEntity(entity);
				client.execute(method);
			}
		} catch (IOException e) {
			String message = NLS.bind(Messages.DefaultMarketplaceService_cannotCompleteRequest_reason,
					location.toString(), e.getMessage());
			throw new CoreException(createErrorStatus(message, e));
		} finally {
			client.getConnectionManager().shutdown();
		}
	}

	public void reportInstallSuccess(INode node, IProgressMonitor monitor) {
		String url = node.getUrl();
		if (!url.endsWith("/")) { //$NON-NLS-1$
			url += "/"; //$NON-NLS-1$
		}
		url += "success"; //$NON-NLS-1$
		url = addMetaParameters(url);
		try {
			InputStream stream = transport.stream(new URI(url), monitor);

			try {
				while (stream.read() != -1) {
					// nothing to do
				}
			} finally {
				stream.close();
			}
		} catch (Throwable e) {
			//per bug 314028 logging this error is not useful.
		}
	}

	public IUserFavoritesService getUserFavoritesService() {
		return userFavoritesService;
	}

	public void setUserFavoritesService(IUserFavoritesService userFavoritesService) {
		this.userFavoritesService = userFavoritesService;
	}
}
