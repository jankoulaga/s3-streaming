package com.ju

import java.io.ByteArrayInputStream
import java.util.Date

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import play.api.Logger
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee, Traversable}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}


/**
  * Created by janko on 10/11/14.
  */
sealed trait Chunker {
  val byteSize: Int
  protected def chunk: Enumeratee[Array[Byte], Array[Byte]] = Enumeratee.grouped(Traversable.takeUpTo[Array[Byte]](byteSize).transform(Iteratee.consume()))
}

trait S3 extends Chunker {
  val accessKey: String
  val accessSecret: String
  lazy val credentials = new BasicAWSCredentials(accessKey, accessSecret)
  lazy val s3Client = new AmazonS3Client(credentials)

  def upload(bucket: String, fileName: String, enum: Enumerator[Array[Byte]])(implicit ec: ExecutionContext): Future[S3UploadResult] = {

    val initRequest = new InitiateMultipartUploadRequest(bucket, fileName)
    val initResponse = s3Client.initiateMultipartUpload(initRequest)
    val uploadId = initResponse.getUploadId
    // compose the data enumerator with an enumeratee from chunk giving it an iteratee from the uploader val.
    val futETags = enum.through(chunk) |>>> uploaderProcess(fileName, bucket, uploadId)

    futETags.map(completeUpload(bucket, fileName, uploadId)).recoverWith {
      case e: Exception =>
        s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(bucket, fileName, uploadId))
        Logger.error("Error while performing a multipart upload to S3", e)
        Future.failed(S3UploadException("Error while performing a multipart upload to S3", e))
    }
  }

  def completeUpload(bucket: String, fileName: String, uploadId: String): (Seq[PartETag]) => S3UploadResult = { etags =>
    val compRequest = new CompleteMultipartUploadRequest(bucket, fileName, uploadId, etags.toBuffer[PartETag])
    composeResult(s3Client.completeMultipartUpload(compRequest))
  }

  def uploaderProcess(fileName: String, bucket: String, uploadId: String)(implicit ec: ExecutionContext): Iteratee[Array[Byte], Seq[PartETag]] = {
    Iteratee.foldM[Array[Byte], Seq[PartETag]](Seq.empty) {
      case (etags, bytes) =>
        val uploadRequest = composeRequest(fileName, bucket, uploadId, etags, bytes)
        //create a future with a list of etags, appending the new ones to the previous seq.
        Future(s3Client.uploadPart(uploadRequest).getPartETag).map(etags :+ _)
    }
  }

  def composeRequest(fileName: String, bucket: String, uploadId: String, etags: Seq[PartETag], bytes: Array[Byte]): UploadPartRequest = {
    new UploadPartRequest().withBucketName(bucket).withKey(fileName).withPartNumber(etags.length + 1)
      .withUploadId(uploadId).withInputStream(new ByteArrayInputStream(bytes)).withPartSize(bytes.length)
  }

  def composeResult(r: CompleteMultipartUploadResult) = S3UploadResult(r.getBucketName, r.getKey, r.getLocation, r.getETag, r.getVersionId, r.getExpirationTime)

}

case class S3UploadResult(bucketName: String, key: String, location: String, eTag: String, versionId: String, expiration: Date)

case class S3UploadException(message: String, throwable: Throwable) extends Exception(message, throwable)

case class S3Upload(override val accessKey: String, override val accessSecret: String, override val byteSize: Int) extends S3
