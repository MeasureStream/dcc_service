package de.ptb.dcc.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.time.Duration;

@Service
public class S3Service {

    @Value("${aws.s3.endpoint}")
    private String endpoint;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.public-url}")
    private String publicUrlBase;

    @Value("${aws.accessKeyId}")
    private String accessKey;

    @Value("${aws.secretAccessKey}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        try {
            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .region(Region.of(region))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();

            System.out.println("[INFO] S3Service connecting to endpoint: " + endpoint);
            ensureBucketExists();
            System.out.println("[SUCCESS] S3Service initialized and connected to Garage.");
        } catch (Exception e) {
            System.err.println("[WARNING] S3Service initialization failed!");
            System.err.println("  - Message: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  - Cause: " + e.getCause());
            }
            System.err.println("DCC files will be stored locally instead of S3.");
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } else {
                throw e;
            }
        }
    }

    public String uploadFile(String key, File file, String contentType) {
        if (s3Client == null) {
            System.err.println("[ERROR] S3Client not initialized. Cannot upload " + key);
            return null;
        }
        try {
            System.out.println(
                    "[INFO] Uploading file to S3: " + key + " (Bucket: " + bucket + ", Endpoint: " + endpoint + ")");
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));

            String publicUrl = publicUrlBase + "/" + key;
            System.out.println("[SUCCESS] Uploaded " + key + " to S3. Public URL: " + publicUrl);
            return publicUrl;
        } catch (Exception e) {
            System.err.println("[ERROR] S3 Upload failed for " + key + "!");
            System.err.println("  - Error Message: " + e.getMessage());
            System.err.println("  - Cause: " + e.getCause());
            if (e.getMessage() != null && e.getMessage().contains("UnknownHostException")) {
                System.err.println("  - Troubleshooting: This usually means the endpoint '" + endpoint
                        + "' is not reachable from this container.");
            }
            return null;
        }
    }

    public byte[] downloadFile(String key) {
        if (s3Client == null) {
            System.err.println("[ERROR] S3Client not initialized. Cannot download " + key);
            return null;
        }
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            return objectBytes.asByteArray();
        } catch (Exception e) {
            System.err.println("[ERROR] S3 Download failed for " + key + ": " + e.getMessage());
            return null;
        }
    }
}
