package org.healthflow.dp.status.functions;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.healthflow.dp.core.function.BaseDispatcherFunction;
import org.healthflow.dp.core.function.ValidationResult;
import org.healthflow.dp.core.util.PostgresConnect;
import org.healthflow.dp.status.task.StatusSearchConfig;

import java.util.Map;

public class StatusSearchProcessFunction extends BaseDispatcherFunction {

    private Logger logger = LoggerFactory.getLogger(StatusSearchProcessFunction.class);
    private StatusSearchConfig config;

    public StatusSearchProcessFunction(StatusSearchConfig config) {
        super(config);
        this.config = config;
    }

    @Override
    public ValidationResult validate(Map<String, Object> event) {
        // TODO: Add domain specific validations
        return new ValidationResult(true, null);
    }

}


