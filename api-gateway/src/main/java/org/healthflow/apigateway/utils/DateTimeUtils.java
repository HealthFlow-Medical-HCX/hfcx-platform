package org.healthflow.apigateway.utils;

import lombok.experimental.UtilityClass;
import org.healthflow.apigateway.exception.ClientException;
import org.healthflow.apigateway.exception.ErrorCodes;
import org.joda.time.DateTime;

import java.text.MessageFormat;
import static org.healthflow.common.response.ResponseMessage.INVALID_TIMESTAMP_MSG;

@UtilityClass
public class DateTimeUtils {

    public static boolean validTimestamp(int range, String timestamp) throws ClientException {
        try {
            DateTime requestTime = new DateTime(timestamp);
            DateTime currentTime = DateTime.now();
            return (!requestTime.isBefore(currentTime.minusHours(range)) && !requestTime.isAfter(currentTime));
        } catch (Exception e) {
            throw new ClientException(ErrorCodes.ERR_INVALID_TIMESTAMP, MessageFormat.format(INVALID_TIMESTAMP_MSG, e.getMessage()));
        }
    }

}
