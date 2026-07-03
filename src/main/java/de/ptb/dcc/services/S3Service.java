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

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    public String uploadBytes(String key, byte[] data, String contentType) {
        if (s3Client == null) {
            System.err.println("[ERROR] S3Client not initialized. Cannot upload " + key);
            return null;
        }
        try {
            System.out.println(
                    "[INFO] Uploading bytes to S3: " + key + " (Bucket: " + bucket + ", Endpoint: " + endpoint + ")");
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));

            String publicUrl = publicUrlBase + "/" + key;
            System.out.println("[SUCCESS] Uploaded " + key + " to S3. Public URL: " + publicUrl);
            return publicUrl;
        } catch (Exception e) {
            System.err.println("[ERROR] S3 Upload failed for " + key + "!");
            System.err.println("  - Error Message: " + e.getMessage());
            return null;
        }
    }

    public String uploadPath(String key, Path path, String contentType) {
        if (s3Client == null) {
            System.err.println("[ERROR] S3Client not initialized. Cannot upload " + key);
            return null;
        }
        try {
            return uploadBytes(key, Files.readAllBytes(path), contentType);
        } catch (Exception e) {
            System.err.println("[ERROR] S3 Upload failed for " + key + ": " + e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() {
        return s3Client != null;
    }

    public List<String> listKeys(String prefix) {
        if (s3Client == null) return List.of();
        try {
            ListObjectsRequest req = ListObjectsRequest.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();
            return s3Client.listObjects(req).contents().stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("[ERROR] S3 listKeys failed for prefix " + prefix + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Deletes every object under the given key prefix. Used before re-uploading a
     * calibration run's artefacts, so a stale object from a previous attempt (e.g. a
     * fig5_post_residuals.png uploaded when a parameter adjustment was applied, still
     * sitting there after a later no-adjustment re-run that no longer generates it)
     * does not linger and get served to the frontend.
     *
     * Safe to call with a prefix that has no matching objects (no-op).
     */
    public int deleteObjectsByPrefix(String prefix) {
        if (s3Client == null) return 0;
        List<String> keys = listKeys(prefix);
        if (keys.isEmpty()) return 0;
        try {
            List<ObjectIdentifier> toDelete = keys.stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .collect(Collectors.toList());
            DeleteObjectsRequest req = DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build();
            s3Client.deleteObjects(req);
            System.out.println("[INFO] Deleted " + toDelete.size() + " stale S3 object(s) under prefix: " + prefix);
            return toDelete.size();
        } catch (Exception e) {
            System.err.println("[ERROR] S3 deleteObjectsByPrefix failed for prefix " + prefix + ": " + e.getMessage());
            return 0;
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
