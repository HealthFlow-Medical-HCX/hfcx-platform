package org.healthflow.hcx.services.sms;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * AWS SNS-backed SMS gateway. Used as a fallback / non-production gateway
 * (active for {@code aws-fallback}, {@code dev} and {@code test} profiles).
 */
@Service
@Profile({"aws-fallback", "dev", "test"})
public class AwsSnsSmsGateway implements SmsGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsSnsSmsGateway.class);
    private static final String NAME = "aws-sns";

    @Value("${aws.accessKey}")
    private String accessKey;

    @Value("${aws.accessSecret}")
    private String accessSecret;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${sms.aws.senderId:HEALTHFLOW}")
    private String senderId;

    @Value("${sms.aws.smsType:Transactional}")
    private String smsType;

    @Override
    public void send(String phoneNumber, String message) throws Exception {
        AmazonSNS snsClient = AmazonSNSClient.builder()
                .withCredentials(new AWSCredentialsProvider() {
                    @Override
                    public AWSCredentials getCredentials() {
                        return new BasicAWSCredentials(accessKey, accessSecret);
                    }

                    @Override
                    public void refresh() {
                        // no-op: static credentials
                    }
                })
                .withRegion(awsRegion)
                .build();

        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put("AWS.SNS.SMS.SenderID", new MessageAttributeValue()
                .withStringValue(senderId)
                .withDataType("String"));
        attributes.put("AWS.SNS.SMS.SMSType", new MessageAttributeValue()
                .withStringValue(smsType)
                .withDataType("String"));

        PublishRequest request = new PublishRequest()
                .withMessage(message)
                .withPhoneNumber(phoneNumber)
                .withMessageAttributes(attributes);

        PublishResult result = snsClient.publish(request);
        LOGGER.debug("AWS SNS SMS sent: messageId={} recipient={}", result.getMessageId(), phoneNumber);
    }

    @Override
    public String name() {
        return NAME;
    }
}
