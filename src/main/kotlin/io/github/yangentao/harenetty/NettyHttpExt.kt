@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.github.yangentao.hare.HttpContext
import io.github.yangentao.httpbasic.HttpHeader
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import java.nio.charset.Charset

@Suppress("LocalVariableName", "DEPRECATION")
internal fun trace(ctx: ChannelHandlerContext, request: FullHttpRequest, requestURI: String) {
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    val CRLF = "\r\n"
    val buffer = StringBuilder("TRACE ").append(requestURI).append(" ").append(request.protocolVersion().text())
    val headers = request.headers()
    for (e in headers) {
        buffer.append(CRLF).append(e.key).append(": ").append(request.getHeader(e.value))
    }
    buffer.append(CRLF)
    val data = buffer.toString().toByteArray()
    response.setContentType("message/http")
    response.setContentLength(data.size)
    response.content().writeBytes(data)
    ctx.writeAndFlush(response)
}

internal fun options(ctx: ChannelHandlerContext, request: FullHttpRequest, options: Set<String>) {
    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    val ls = LinkedHashSet<String>()
    ls.addAll(options)
    if (io.github.yangentao.httpbasic.HttpMethod.GET in options) {
        ls += io.github.yangentao.httpbasic.HttpMethod.HEAD
    }
    ls += io.github.yangentao.httpbasic.HttpMethod.OPTIONS
//        ls += HttpMethod.TRACE
    val methods = ls.joinToString(",")
    response.setHeader("Allow", methods)
    val origin = request.getHeader("Origin")
    if (origin != null) {
        response.setHeader("Access-Control-Allow-Origin", origin)
        response.setHeader("Access-Control-Allow-Credentials", "true")
        response.setHeader("Access-Control-Allow-Methods", methods)
        response.setHeader(
            "Access-Control-Allow-Headers",
            "Origin,Accept,Content-Type,Content-Length,X-Requested-With,Key,Token,Authorization"
        )
    }
    ctx.writeAndFlush(response)
}

fun ChannelHandlerContext.error404NotFound() {
    this.writeAndFlush(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND))
}

fun ChannelHandlerContext.error403Forbidden() {
    this.writeAndFlush(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN))
}

fun ChannelFuture.thenMaybeClose(context: NettyHttpContext): ChannelFuture {
    if (!context.request.isKeepAlive) {
        addListener(ChannelFutureListener.CLOSE)
    }
    return this
}

val HttpMessage.isKeepAlive: Boolean get() = HttpUtil.isKeepAlive(this)
val HttpMessage.mimeType: String? get() = HttpUtil.getMimeType(this)?.toString()?.lowercase()
val HttpRequest.isPostRawData: Boolean
    get() {
        val ct = this.mimeType
        return this.method() == HttpMethod.POST && ct != HttpContext.MIME_MULTIPART && ct != HttpContext.MIME_WWW_FORM_URLENCODED
    }

fun HttpMessage.setHeader(name: CharSequence, value: Any) {
    this.headers().set(name, value)
}

fun HttpMessage.getHeader(name: CharSequence): String? {
    return this.headers().get(name)
}

fun HttpMessage.setContentLength(length: Int) {
    this.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, length)
}

fun HttpMessage.setContentType(contentType: String) {
    this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
}

fun FullHttpResponse.setContentLengthByBuf() {
    if (content().size > 0) this.setContentLength(content().size)
}

fun HttpMessage.setHeader(header: HttpHeader) {
    this.headers().set(header.name, header.value)
}

fun HttpContent.writeString(value: String, charset: Charset = Charsets.UTF_8) {
    this.content().writeCharSequence(value, charset)
}

fun HttpContent.writeLine(value: String, charset: Charset = Charsets.UTF_8) {
    this.content().writeCharSequence(value + "\r\n", charset)
}

fun HttpContent.writeLine() {
    writeLine("")
}


