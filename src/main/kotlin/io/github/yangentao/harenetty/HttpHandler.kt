package io.github.yangentao.harenetty


import io.github.yangentao.hare.HttpApp
import io.github.yangentao.hare.TargetRouterAction
import io.github.yangentao.hare.log.logd
import io.github.yangentao.hare.utils.istart
import io.github.yangentao.httpbasic.HttpMethod
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.WriteTimeoutException

//精确匹配 -》 模糊匹配-〉静态文件
@ChannelHandler.Sharable
class HttpHandler(val app: HttpApp) : io.netty.channel.SimpleChannelInboundHandler<FullHttpRequest>() {
    val contextPath: String get() = app.contextPath

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        logd("URI: ", msg.uri())
        val method = msg.method().name()

        val queryDecoder = QueryStringDecoder(msg.uri())
        val requestUri = queryDecoder.path()

        if (method == HttpMethod.TRACE) {
            trace(ctx, msg, requestUri)
            return
        }

        if (!(requestUri istart  contextPath)) {
            ctx.error404NotFound()
            return
        }
        val p: TargetRouterAction? = app.findRouter(requestUri)
        if (p == null) {
            ctx.error404NotFound()
            return
        }
        if (method == HttpMethod.OPTIONS) {
            options(ctx, msg, p.action.methods)
            return
        }
        if (!p.checkMethods(method)) {
            ctx.error403Forbidden()
            return
        }
        val context = NettyHttpContext(app, ctx, p.routePath, queryDecoder, msg, DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
        context.cors()
        p.process(context)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        super.userEventTriggered(ctx, evt)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is ReadTimeoutException || cause is WriteTimeoutException) {
            ctx.close()
            return
        }
        super.exceptionCaught(ctx, cause)
    }

}