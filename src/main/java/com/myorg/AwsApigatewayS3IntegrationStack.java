package com.myorg;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigateway.AwsIntegration;
import software.amazon.awscdk.services.apigateway.IntegrationOptions;
import software.amazon.awscdk.services.apigateway.IntegrationResponse;
import software.amazon.awscdk.services.apigateway.MethodOptions;
import software.amazon.awscdk.services.apigateway.MethodResponse;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.iam.ServicePrincipalOpts;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketProps;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AwsApigatewayS3IntegrationStack extends Stack {
    public AwsApigatewayS3IntegrationStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AwsApigatewayS3IntegrationStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Bucket bucket = new Bucket(this, "test-scenarios", BucketProps.builder().bucketName("apigatways3integration").build());

        Role role = Role.Builder.create(this, "ApiGatewayS3Role")
                .assumedBy(new ServicePrincipal("apigateway.amazonaws.com", ServicePrincipalOpts.builder().build()))
                .managedPolicies(
                        List.of(
                                ManagedPolicy.fromManagedPolicyArn(
                                        this,
                                        "AmazonS3ReadOnlyAccess",
                                        "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess"
                                )
                        )
                )
                .build();


        RestApi api = RestApi.Builder.create(this, "Scenarios-API")
                .restApiName("Scenarios").description("This service services test scenarios.")
                .build();

        HashMap<String, String> requestTemplates = new HashMap<>();
        requestTemplates.put("application/json", "" +
                "#set($scenario = $input.params('scenario'))\n" +
                "#set($nextPage = $input.params('nextPage'))\n" +
                "#if( $nextPage == '') \n" +
                "  #set($context.requestOverride.path.nextPage = $scenario + 'F1')\n" +
                "#else \n" +
                "  #set($context.requestOverride.path.nextPage = $nextPage)\n" +
                "#end");

        AwsIntegration awsIntegration = AwsIntegration.Builder.create()
                .service("s3")
                .integrationHttpMethod("GET")
                .path(bucket.getBucketName() + "/{nextPage}")
                .options(IntegrationOptions.builder()
                        .requestTemplates(requestTemplates)
                        .integrationResponses(
                                List.of(
                                        IntegrationResponse.builder().statusCode("200").build(),
                                        IntegrationResponse.builder().selectionPattern("4\\d{2}").statusCode("400").build(),
                                        IntegrationResponse.builder().selectionPattern("5\\d{2}").statusCode("500").build()))
                        .credentialsRole(role).build())
                .build();

        HashMap<String, Boolean> requestParameters = new HashMap<>();
        requestParameters.put("method.request.querystring.nextPage", false);
        requestParameters.put("method.request.querystring.scenario", false);

        HashMap<String, Boolean> responseParameters = new HashMap<>();
        responseParameters.put("method.response.header.Content-Length", true);
        responseParameters.put("method.response.header.Content-Type", true);
        responseParameters.put("method.response.header.Date", true);

        api.getRoot()
                .addMethod("GET", awsIntegration,
                        MethodOptions.builder()
                                .requestParameters(requestParameters)
                                .methodResponses(Arrays.asList(
                                        MethodResponse.builder().statusCode("200")
                                                .responseParameters(responseParameters)
                                                .build(),
                                        MethodResponse.builder().statusCode("400").build(),
                                        MethodResponse.builder().statusCode("500").build()))
                                .build());
    }
}
