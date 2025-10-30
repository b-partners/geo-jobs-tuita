package app.bpartners.geojobs.file.bucket;

import static java.io.File.createTempFile;

import app.bpartners.geojobs.PojaGenerated;
import app.bpartners.geojobs.file.hash.FileHash;
import app.bpartners.geojobs.file.hash.FileHashAlgorithm;
import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

@PojaGenerated
@SuppressWarnings("all")
@Component
@AllArgsConstructor
public class BucketComponent {
  private static final Duration DEFAULT_PRE_SIGNED_URL_DURATION = Duration.ofHours(1L);

  private final BucketConf bucketConf;

  public FileHash upload(File file, String bucketKey) {
    return file.isDirectory() ? uploadDirectory(file, bucketKey) : uploadFile(file, bucketKey);
  }

  private FileHash uploadDirectory(File file, String bucketKey) {
    var request =
        UploadDirectoryRequest.builder()
            .source(file.toPath())
            .bucket(bucketConf.getBucketName())
            .s3Prefix(bucketKey)
            .build();
    var upload = bucketConf.getS3TransferManager().uploadDirectory(request);
    var uploaded = upload.completionFuture().join();
    if (!uploaded.failedTransfers().isEmpty()) {
      throw new RuntimeException("Failed to upload following files: " + uploaded.failedTransfers());
    }
    return new FileHash(FileHashAlgorithm.NONE, null);
  }

  private FileHash uploadFile(File file, String bucketKey) {
    var request =
        UploadFileRequest.builder()
            .source(file)
            .putObjectRequest(req -> req.bucket(bucketConf.getBucketName()).key(bucketKey))
            .addTransferListener(LoggingTransferListener.create())
            .build();
    var upload = bucketConf.getS3TransferManager().uploadFile(request);
    var uploaded = upload.completionFuture().join();
    return new FileHash(FileHashAlgorithm.SHA256, uploaded.response().checksumSHA256());
  }

  @SneakyThrows
  public File download(String bucketKey) {
    var destination =
        createTempFile(prefixFromBucketKey(bucketKey), suffixFromBucketKey(bucketKey));
    FileDownload download =
        bucketConf
            .getS3TransferManager()
            .downloadFile(
                DownloadFileRequest.builder()
                    .getObjectRequest(
                        GetObjectRequest.builder()
                            .bucket(bucketConf.getBucketName())
                            .key(bucketKey)
                            .build())
                    .destination(destination)
                    .build());
    download.completionFuture().join();
    return destination;
  }

  private String prefixFromBucketKey(String bucketKey) {
    return lastNameSplitByDot(bucketKey)[0];
  }

  private String suffixFromBucketKey(String bucketKey) {
    var splitByDot = lastNameSplitByDot(bucketKey);
    return splitByDot.length == 1 ? "" : splitByDot[splitByDot.length - 1];
  }

  private String[] lastNameSplitByDot(String bucketKey) {
    var splitByDash = bucketKey.split("/");
    var lastName = splitByDash[splitByDash.length - 1];
    return lastName.split("\\.");
  }

  public URL presign(String bucketKey, Duration expiration) {
    return presign(bucketKey, expiration, Optional.empty());
  }

  // TODO: move to customComponent
  public URL presign(String bucketKey, Duration expiration, Optional<String> fileName) {
    var requestBuilder =
        GetObjectRequest.builder().bucket(bucketConf.getBucketName()).key(bucketKey);
    if (fileName.isPresent()) {
      requestBuilder.responseContentDisposition(
          "attachment; filename=" + "\"" + fileName.get() + "\"");
    }
    GetObjectRequest getObjectRequest = requestBuilder.build();
    PresignedGetObjectRequest presignedRequest =
        bucketConf
            .getS3Presigner()
            .presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build());
    return presignedRequest.url();
  }

  public String presign(String bucketKey) {
    if (bucketKey == null) {
      return null;
    }
    return presign(bucketKey, DEFAULT_PRE_SIGNED_URL_DURATION).toString();
  }

  public String getBucketName() {
    return bucketConf.getBucketName();
  }
}
