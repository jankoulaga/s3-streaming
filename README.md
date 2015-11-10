# s3-streaming

This library uses Play 2.x.x Iteratee, Enumeratee features to issue multiple multipart requests to upload a file to Amazon S3 buckets, allowing you to send enormous amounts of data through the wire without using a lot of resources.

Under the hood it uses AmazonAWS Java SDK to issue those calls, and Play libraries(as provided) to handle the whole Iteratee, Enumeratee magic.

If you need to customise it to your version of Scala/Play just change the ```scalaVersion``` and ```playVersion``` vals in build.sbt

Main idea was to be able to use any type of an ```Enumerator```, map it to an array of bytes if you have to, and send it to your S3 bucket.

There's already a case class ```S3Upload``` where you can pass your bucket name, file name to be stored in a bucket, AWS credentials and desired chunk size, but if you want to use a different approach, just extend the S3 trait.


For example, if you want to write a file to S3 you can do it like this:
```scala
val fileEnumerator = Enumerator.fromFile(new File("/somecrappyfile"))
S3Upload("key", "pssst", 5 * 1024).upload("my-s3-bucket","this_will_be_my_filename", fileEnumerator)

```

The upload function returns a Future of a 
```scala
case class S3UploadResult(bucketName: String, key: String, location: String, eTag: String, versionId: String, expiration: Date)
```
So this yucky example could look like:

```scala
val fileEnumerator = Enumerator.fromFile(new File("/somecrappyfile"))
val result = S3Upload("key", "pssst", 5 * 1024).upload("my-s3-bucket", "this_will_be_my_filename", fileEnumerator)
result.onComplete{
  case Success(msg) =>
    println(s"Upload to ${msg.bucketName} is done. You can fetch it from ${msg.location} until ${msg.expiration}")
  case Failure(ex) =>
    ex.printStackTrace()
}
```
