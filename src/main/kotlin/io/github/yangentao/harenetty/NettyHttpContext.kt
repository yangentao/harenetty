package io.github.yangentao.harenetty

import io.github.yangentao.hare.FileRange
import io.github.yangentao.hare.HttpApp
import io.github.yangentao.hare.HttpContext
import io.github.yangentao.hare.HttpResult
import io.github.yangentao.hare.utils.UriPath
import io.github.yangentao.hare.utils.encodedURL
import io.github.yangentao.hare.utils.quoted
import io.github.yangentao.hare.utils.uuidString
import io.github.yangentao.httpbasic.*
import io.github.yangentao.types.MB
import io.github.yangentao.types.appendAll
import io.github.yangentao.types.appendValue
import io.github.yangentao.types.invokeInstance
import io.github.yangentao.types.proxyInterface
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelProgressiveFuture
import io.netty.channel.ChannelProgressiveFutureListener
import io.netty.channel.DefaultFileRegion
import io.netty.handler.codec.DateFormatter
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.multipart.*
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Method
import java.nio.file.Files
import java.util.*
import kotlin.collections.first
import kotlin.collections.iterator

//TODO 处理action直接返回错误码, 比如 404
class NettyHttpContext(
    override val app: HttpApp,
    private val _channelContext: ChannelHandlerContext,
    override val routePath: UriPath,
    val queryDecoder: QueryStringDecoder,
    val request: FullHttpRequest,
    val response: DefaultFullHttpResponse
) : HttpContext() {
    val channelContext: ChannelHandlerContext = proxyInterface<ChannelHandlerContext> { method: Method, args: Array<out Any?>? ->
        if (method.name == "write" || method.name == "writeAndFlush") {
            commited = true
        }
        method.invokeInstance(_channelContext, args)
    }

    override val requestUri: String = queryDecoder.path()

    override var commited: Boolean = false
        private set

    override val requestContent: ByteArray? by lazy { request.content().bytesCopy }

    init {
        parseParams()
    }

    override val queryString: String? get() = queryDecoder.rawQuery()

    override val method: String
        get() = request.method().name()

    override fun requestHeader(name: String): String? {
        return request.getHeader(name)
    }

    override fun responseHeader(name: String, value: Any) {
        response.setHeader(name, value)
    }

    override val removeAddress: String
        get() {
            val a = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            if (a != null && a.isNotEmpty()) {
                return a
            }
            val b = request.getHeader("X-Real-IP")?.trim()
            if (b != null && b.isNotEmpty()) {
                return b
            }
            return channelContext.channel().remoteAddress().toString()
        }

    private fun parseParams() {
        val q: QueryStringDecoder = queryDecoder
        for (e in q.parameters()) {
            this.paramMap.appendAll(e.key.substringBefore('['), e.value)
        }

        if (request.method() == HttpMethod.POST) {
            val fac = DefaultHttpDataFactory(1L * MB)
            val decoder = HttpPostRequestDecoder(fac, request)
            val httpDataList: List<InterfaceHttpData> = decoder.bodyHttpDatas
            for (item in httpDataList) {
                if (item is Attribute) {
                    this.paramMap.appendValue(item.name, item.value)
                } else if (item is FileUpload) {
                    val file: File = Files.createTempFile(null, null).toFile()
                    if (item.isInMemory) {
                        file.writeBytes(item.content().bytesCopy)
                    } else {
                        item.file.renameTo(file)
                    }
                    fileUploads += HttpFileParam(item.name, HttpFile(file, item.filename, item.contentType ?: Mimes.ofFile(item.filename)))
                }
            }
            for (item in httpDataList) {
                item.release();
                fac.removeHttpDataFromClean(request, item);
            }
            fac.cleanAllHttpData();
            decoder.destroy();
        }
    }

    override fun send(result: HttpResult) {
        this.response.status = HttpResponseStatus.valueOf(result.status.code)
        for ((k, v) in result.headers) {
            response.headers().set(k, v)
        }
        if (!result.isEmptyContent) {
            if (!result.containsHeader(HttpHeader.CONTENT_LENGTH)) {
                response.setContentLength(result.contentLength)
            }
            response.content().writeBytes(result.content)
        }
        channelContext.writeAndFlush(response)
    }

    override fun sendError(status: HttpStatus) {
        response.status = HttpResponseStatus.valueOf(status.code, status.reason)
        channelContext.writeAndFlush(response)
    }

    override fun sendFile(httpFile: HttpFile, attachment: Boolean) {
        if (!httpFile.file.exists() || !httpFile.file.canRead()) return sendError404()
        val file: File = httpFile.file
        val fileLength: Long = file.length()
        val lastModified: Long = file.lastModified() / 1000 * 1000
        val etag: String = "$fileLength-$lastModified"

        val resp = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        val old = response.headers()
        for (name in old.names()) {
            resp.headers().set(name, old.get(name))
        }

        resp.setContentType(httpFile.mime)

        if (!ifMatchEtag(etag)) {
            if (requestHeader(HttpHeader.RANGE) != null) {
                sendError(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)//416
            } else {
                sendError(HttpStatus.PRECONDITION_FAILED)//412
            }
            return
        }

        resp.setHeader(HttpHeaderNames.ETAG, etag.quoted)
        resp.setLastModified(lastModified)
        val seconds = 3600 * 24 * 30 * 6
        resp.setHeader(HttpHeaderNames.CACHE_CONTROL, "public, max-age=$seconds")

        if (!ifModifiedSince(lastModified) || !ifNoneMatch(etag)) {
            resp.status = HttpResponseStatus.NOT_MODIFIED
            channelContext.write(resp)
            channelContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
            return
        }

        if (attachment) {
            resp.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=${httpFile.filename.encodedURL.quoted}")
        }
        val ranges: List<FileRange> = headerRanges(etag, lastModified, fileLength)
        fun writeRange(start: Long, size: Long) {
            val raf = RandomAccessFile(httpFile.file, "r")
            val cf = DefaultFileRegion(raf.channel, start, size)
            val fu = channelContext.write(cf, channelContext.newProgressivePromise())
            fu.addListener(object : ChannelProgressiveFutureListener {
                override fun operationProgressed(future: ChannelProgressiveFuture?, progress: Long, total: Long) {
                }

                override fun operationComplete(future: ChannelProgressiveFuture) {
                    raf.close()
                }

            })
        }
        if (ranges.isEmpty()) {
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength)
            channelContext.write(resp)
            writeRange(0, fileLength)
            channelContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        } else if (ranges.size == 1) {
            val range = ranges.first()
            resp.setStatus(HttpResponseStatus.PARTIAL_CONTENT)
            resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, range.size)
            resp.setHeader(HttpHeader.CONTENT_RANGE, "bytes ${range.start}-${range.end}/$fileLength")
            channelContext.write(resp)
            writeRange(range.start, range.size)
            channelContext.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        } else {
            @Suppress("LocalVariableName")
            val BOUNDARY = uuidString()
            resp.setStatus(HttpResponseStatus.PARTIAL_CONTENT)
            resp.setContentType("multipart/byteranges; boundary=${BOUNDARY}")
            channelContext.write(resp)

            for (range in ranges) {
                val con = DefaultHttpContent(Unpooled.buffer(512))
                con.writeLine()
                con.writeLine("--${BOUNDARY}")
                con.writeLine("Content-Type: ${httpFile.mime}")
                con.writeLine("Content-Range: bytes ${range.start}-${range.end}/$fileLength")
                con.writeLine()
                channelContext.write(con)
                writeRange(range.start, range.size)
            }
            val last = DefaultLastHttpContent(Unpooled.buffer(200))
            last.writeLine()
            last.writeLine("--${BOUNDARY}--")
            channelContext.write(last)
        }
    }
}

//https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Headers/If-Match
private fun HttpMessage.ifMatch(etag: String): Boolean {
    val matchValue: String = getHeader(HttpHeader.IF_MATCH)?.substringAfter('/')?.trim() ?: return true
    if (matchValue == "*") return true
    val e = etag.trim('"')
    val ls = matchValue.split(',').map { it.trim('"', ' ') }
    return ls.any { it == e }
}

//If-None-Match: "bfc13a64729c4290ef5b2c2730249c88ca92d82d"
//If-None-Match: W/"67ab43", "54ed21", "7892dd"
private fun HttpMessage.ifNoneMatch(etag: String): Boolean {
    val matchValue: String = getHeader(HttpHeader.IF_NONE_MATCH)?.substringAfter('/')?.trim() ?: return true
    if (matchValue == "*") return false
    val e = etag.trim('"')
    val ls = matchValue.split(',').map { it.trim('"', ' ') }
    return ls.all { it != e }
}

private fun HttpMessage.isModifiedSince(lastModTime: Long): Boolean {
    val isModSince = this.headers().getTimeMillis(HttpHeaderNames.IF_MODIFIED_SINCE, -1)
    return isModSince == -1L || (isModSince / 1000) < (lastModTime / 1000)
}

private fun HttpMessage.setLastModified(lastModTime: Long) {
    this.setHeader(HttpHeaderNames.LAST_MODIFIED, DateFormatter.format(Date(lastModTime / 1000 * 1000)))
}

