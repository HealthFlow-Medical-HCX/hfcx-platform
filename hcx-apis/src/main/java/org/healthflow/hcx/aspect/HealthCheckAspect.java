package org.healthflow.hcx.aspect;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.healthflow.common.dto.Response;
import org.healthflow.common.exception.ErrorCodes;
import org.healthflow.common.exception.ServiceUnavailbleException;
import org.healthflow.common.utils.Constants;
import org.healthflow.common.utils.JSONUtils;
import org.healthflow.hcx.managers.HealthCheckManager;

@Aspect
@Component
public class HealthCheckAspect {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckAspect.class);

    @Autowired
    private HealthCheckManager healthCheckManager;

    @Before("execution(* org.healthflow.hcx.controllers.v1.*.*(..))")
    public void healthCheckBeforeEachAPICall() throws JsonProcessingException, ServiceUnavailbleException {
        if (!HealthCheckManager.allSystemHealthResult) {
            Response healthResp = healthCheckManager.checkAllSystemHealth();
            if (!(boolean) healthResp.get(Constants.HEALTHY)) {
                logger.error("Health check is failed : " + JSONUtils.serialize(healthResp.get(Constants.CHECKS)));
                throw new ServiceUnavailbleException(ErrorCodes.ERR_SERVICE_UNAVAILABLE, "The server is temporarily unable to service your request. Please try again later.");
            } else getSuccessLogger();
        } else getSuccessLogger();
    }

    private void getSuccessLogger() {
        logger.debug("Health check is successful");
    }
}