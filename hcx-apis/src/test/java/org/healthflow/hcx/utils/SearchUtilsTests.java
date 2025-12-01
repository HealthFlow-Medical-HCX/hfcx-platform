package org.healthflow.hcx.utils;

import org.elasticsearch.action.search.SearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.healthflow.common.dto.AuditSearchRequest;
import org.healthflow.common.utils.Constants;
import org.healthflow.hcx.controllers.BaseSpec;
import org.healthflow.hcx.handlers.EventHandler;
import org.healthflow.hcx.service.NotificationService;
import org.healthflow.hcx.service.ParticipantService;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;


@ContextConfiguration(classes={SearchUtil.class,NotificationService.class, EventHandler.class, ParticipantService.class})
class SearchUtilsTests extends BaseSpec {

    @Test
    void buildSearchRequestTest() {
        SearchRequest result = SearchUtil.buildSearchRequest("hcx_audit",new AuditSearchRequest());
        assertNotNull(result);
    }

    @Test
    void buildSearchRequestSuccessTest() {
        AuditSearchRequest searchRequest = new AuditSearchRequest();
        searchRequest.setFilters(new HashMap<String, String>() {{
            put("status","submitted");
        }});
        searchRequest.setAction(Constants.AUDIT_SEARCH);
        SearchRequest result = SearchUtil.buildSearchRequest("hcx_audit",searchRequest);
        assertEquals("hcx_audit", result.indices()[0]);
    }

    @Test
    void buildSearchRequestErrorTest() {
        SearchRequest result = SearchUtil.buildSearchRequest("hcx_audit",null);
        assertNull(result);
    }

}
