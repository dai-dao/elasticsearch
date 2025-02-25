/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.security.rest.action.user;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.RestApiVersion;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestBuilderListener;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xpack.core.security.SecurityContext;
import org.elasticsearch.xpack.core.security.action.user.GetUserPrivilegesRequestBuilder;
import org.elasticsearch.xpack.core.security.action.user.GetUserPrivilegesResponse;
import org.elasticsearch.xpack.core.security.authz.RoleDescriptor;
import org.elasticsearch.xpack.core.security.authz.privilege.ConfigurableClusterPrivilege;
import org.elasticsearch.xpack.core.security.authz.privilege.ConfigurableClusterPrivileges;
import org.elasticsearch.xpack.core.security.user.User;
import org.elasticsearch.xpack.security.rest.action.SecurityBaseRestHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.elasticsearch.rest.RestRequest.Method.GET;

/**
 * REST handler that list the privileges held by a user.
 */
public class RestGetUserPrivilegesAction extends SecurityBaseRestHandler {

    private final SecurityContext securityContext;

    public RestGetUserPrivilegesAction(Settings settings, SecurityContext securityContext, XPackLicenseState licenseState) {
        super(settings, licenseState);
        this.securityContext = securityContext;
    }

    @Override
    public List<Route> routes() {
        return List.of(
            Route.builder(GET, "/_security/user/_privileges").replaces(GET, "/_xpack/security/user/_privileges", RestApiVersion.V_7).build()
        );
    }

    @Override
    public String getName() {
        return "security_user_privileges_action";
    }

    @Override
    public RestChannelConsumer innerPrepareRequest(RestRequest request, NodeClient client) throws IOException {
        final User user = securityContext.getUser();
        if (user == null) {
            return restChannel -> { throw new ElasticsearchSecurityException("there is no authenticated user"); };
        }
        final String username = user.principal();
        final GetUserPrivilegesRequestBuilder requestBuilder = new GetUserPrivilegesRequestBuilder(client).username(username);
        return channel -> requestBuilder.execute(new RestListener(channel));
    }

    // Package protected for testing
    static class RestListener extends RestBuilderListener<GetUserPrivilegesResponse> {
        RestListener(RestChannel channel) {
            super(channel);
        }

        @Override
        public RestResponse buildResponse(GetUserPrivilegesResponse response, XContentBuilder builder) throws Exception {
            builder.startObject();

            builder.field(RoleDescriptor.Fields.CLUSTER.getPreferredName(), response.getClusterPrivileges());
            builder.startArray(RoleDescriptor.Fields.GLOBAL.getPreferredName());
            for (ConfigurableClusterPrivilege ccp : response.getConditionalClusterPrivileges()) {
                ConfigurableClusterPrivileges.toXContent(builder, ToXContent.EMPTY_PARAMS, Collections.singleton(ccp));
            }
            builder.endArray();

            builder.field(RoleDescriptor.Fields.INDICES.getPreferredName(), response.getIndexPrivileges());
            builder.field(RoleDescriptor.Fields.APPLICATIONS.getPreferredName(), response.getApplicationPrivileges());
            builder.field(RoleDescriptor.Fields.RUN_AS.getPreferredName(), response.getRunAs());

            builder.endObject();
            return new BytesRestResponse(RestStatus.OK, builder);
        }
    }
}
