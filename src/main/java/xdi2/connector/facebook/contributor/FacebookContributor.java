package xdi2.connector.facebook.contributor;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xdi2.connector.facebook.api.FacebookApi;
import xdi2.connector.facebook.mapping.FacebookMapping;
import xdi2.connector.facebook.util.GraphUtil;
import xdi2.core.ContextNode;
import xdi2.core.Graph;
import xdi2.core.features.dictionary.Dictionary;
import xdi2.core.xri3.impl.XRI3Segment;
import xdi2.messaging.GetOperation;
import xdi2.messaging.MessageEnvelope;
import xdi2.messaging.MessageResult;
import xdi2.messaging.exceptions.Xdi2MessagingException;
import xdi2.messaging.target.ExecutionContext;
import xdi2.messaging.target.contributor.AbstractContributor;
import xdi2.messaging.target.contributor.ContributorCall;
import xdi2.messaging.target.interceptor.MessageEnvelopeInterceptor;

public class FacebookContributor extends AbstractContributor implements MessageEnvelopeInterceptor {

	private static final Logger log = LoggerFactory.getLogger(FacebookContributor.class);

	private Graph graph;
	private FacebookApi facebookApi;
	private FacebookMapping facebookMapping;

	public FacebookContributor() {

		super();

		this.getContributors().addContributor(new FacebookUserContributor());
	}

	/*
	 * MessageEnvelopeInterceptor
	 */

	@Override
	public boolean before(MessageEnvelope messageEnvelope, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

		FacebookContributorExecutionContext.resetUsers(executionContext);

		return false;
	}

	@Override
	public boolean after(MessageEnvelope messageEnvelope, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

		return false;
	}

	@Override
	public void exception(MessageEnvelope messageEnvelope, MessageResult messageResult, ExecutionContext executionContext, Exception ex) {

	}

	/*
	 * Sub-Contributors
	 */

	@ContributorCall(addresses={"($)"})
	private class FacebookUserContributor extends AbstractContributor {

		private FacebookUserContributor() {

			super();

			this.getContributors().addContributor(new FacebookUserFieldContributor());
		}

		@Override
		public boolean getContext(XRI3Segment[] contributorXris, XRI3Segment relativeContextNodeXri, XRI3Segment contextNodeXri, GetOperation operation, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

			XRI3Segment facebookContextXri = contributorXris[contributorXris.length - 2];
			XRI3Segment userXri = contributorXris[contributorXris.length - 1];

			log.debug("facebookContextXri: " + facebookContextXri + ", userXri: " + userXri);

			// retrieve the Facebook user ID

			String facebookUserId = null;

			try {

				String accessToken = GraphUtil.retrieveAccessToken(FacebookContributor.this.getGraph(), userXri);
				if (accessToken == null) throw new Exception("No access token.");

				JSONObject user = FacebookContributor.this.retrieveUser(executionContext, accessToken);
				if (user == null) throw new Exception("No user.");

				facebookUserId = user.getString("id");
			} catch (Exception ex) {

				throw new Xdi2MessagingException("Cannot load user data: " + ex.getMessage(), ex, null);
			}

			// add the Facebook user ID to the response

			if (facebookUserId != null) {

				XRI3Segment facebookUserXri = new XRI3Segment("!" + facebookUserId);

				ContextNode facebookUserContextNode = messageResult.getGraph().findContextNode(new XRI3Segment("" + facebookContextXri + facebookUserXri), true);
				ContextNode userContextNode = messageResult.getGraph().findContextNode(new XRI3Segment("" + facebookContextXri + userXri), true);

				Dictionary.setCanonicalContextNode(facebookUserContextNode, userContextNode);
			}

			// done

			return true;
		}
	}

	@ContributorCall(addresses={"($)"})
	private class FacebookUserFieldContributor extends AbstractContributor {

		private FacebookUserFieldContributor() {

			super();
		}

		@Override
		public boolean getContext(XRI3Segment[] contributorXris, XRI3Segment relativeContextNodeXri, XRI3Segment contextNodeXri, GetOperation operation, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

			XRI3Segment facebookContextXri = contributorXris[contributorXris.length - 3];
			XRI3Segment userXri = contributorXris[contributorXris.length - 2];
			XRI3Segment facebookDataXri = contributorXris[contributorXris.length - 1];

			log.debug("facebookContextXri: " + facebookContextXri + ", userXri: " + userXri + ", facebookDataXri: " + facebookDataXri);

			// retrieve the Facebook value

			String facebookValue = null;

			try {

				String facebookUserFieldIdentifier = FacebookContributor.this.facebookMapping.facebookDataXriToFacebookUserFieldIdentifier(facebookDataXri);
				if (facebookUserFieldIdentifier == null) return false;

				String accessToken = GraphUtil.retrieveAccessToken(FacebookContributor.this.getGraph(), userXri);
				if (accessToken == null) throw new Exception("No access token.");

				JSONObject user = FacebookContributor.this.retrieveUser(executionContext, accessToken);
				if (user == null) throw new Exception("No user.");
				if (! user.has(facebookUserFieldIdentifier)) return false;

				facebookValue = user.getString(facebookUserFieldIdentifier);
			} catch (Exception ex) {

				throw new Xdi2MessagingException("Cannot load user data: " + ex.getMessage(), ex, null);
			}

			// add the Facebook value to the response

			if (facebookValue != null) {

				ContextNode contextNode = messageResult.getGraph().findContextNode(contextNodeXri, true);
				contextNode.createLiteral(facebookValue);
			}

			// done

			return true;
		}
	}

	/*
	 * Helper methods
	 */

	private JSONObject retrieveUser(ExecutionContext executionContext, String accessToken) throws IOException, JSONException {

		JSONObject user = FacebookContributorExecutionContext.getUser(executionContext, accessToken);

		if (user == null) {

			user = this.facebookApi.getUser(accessToken);
			FacebookContributorExecutionContext.putUser(executionContext, accessToken, user);
		}

		return user;
	}

	/*
	 * Getters and setters
	 */

	public Graph getGraph() {

		return this.graph;
	}

	public void setGraph(Graph graph) {

		this.graph = graph;
	}

	public FacebookApi getFacebookApi() {

		return this.facebookApi;
	}

	public void setFacebookApi(FacebookApi facebookApi) {

		this.facebookApi = facebookApi;
	}

	public FacebookMapping getFacebookMapping() {

		return this.facebookMapping;
	}

	public void setFacebookMapping(FacebookMapping facebookMapping) {

		this.facebookMapping = facebookMapping;
	}
}
