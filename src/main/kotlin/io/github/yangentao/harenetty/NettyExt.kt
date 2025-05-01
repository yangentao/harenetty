@file:Suppress("unused")

package io.github.yangentao.harenetty

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.util.Attribute
import io.netty.util.AttributeKey
import io.netty.util.concurrent.Future
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty

val ChannelHandlerContext.isActive: Boolean get() = this.channel().isActive

object ChannelProperties {
    operator fun <T : Any> getValue(thisRef: ChannelHandlerContext, property: KProperty<*>): T? {
        return thisRef.prop(property.name)
    }

    operator fun <T : Any> setValue(thisRef: ChannelHandlerContext, property: KProperty<*>, value: T?) {
        if (value == null) {
            thisRef.removeProp(property.name)
        } else {
            thisRef.setProp(property.name, value)
        }
    }
}

class ChannelAttributes(val map: HashMap<String, Any> = HashMap()) : MutableMap<String, Any> by map

private val APP_ATTR: AttributeKey<ChannelAttributes> = AttributeKey.valueOf("app.attr")

val ChannelHandlerContext.propMap: ChannelAttributes
    get() {
        val attrApp: Attribute<ChannelAttributes> = this.channel().attr(APP_ATTR)
        val data = attrApp.get()
        if (data != null) return data
        val a = ChannelAttributes()
        attrApp.set(a)
        return a
    }

fun ChannelHandlerContext.setProp(key: String, value: Any): Any? {
    return this.propMap.put(key, value)
}

fun ChannelHandlerContext.getProp(key: String): Any? {
    return this.propMap[key]
}

fun ChannelHandlerContext.removeProp(key: String): Any? {
    return propMap.remove(key)
}

@Suppress("UNCHECKED_CAST")
fun <T> ChannelHandlerContext.prop(key: String): T? {
    return getProp(key) as? T
}

fun SocketChannel.addLast(vararg handlers: ChannelHandler) {
    this.pipeline().addLast(*handlers)
}

fun SocketChannel.addLast(handlers: List<ChannelHandler>) {
    this.pipeline().addLast(*handlers.toTypedArray())
}

fun ChannelFuture.then(block: (ChannelFuture) -> Unit): ChannelFuture {
    addListener(ChannelFutureListener {
        fun operationComplete(future: ChannelFuture) {
            block(future)
        }
    })
    return this
}

fun ChannelFuture.thenCloseChannel(): ChannelFuture {
    addListener(ChannelFutureListener.CLOSE)
    return this
}

fun ChannelFuture.thenCloseChannelOnFailure(): ChannelFuture {
    addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
    return this
}

fun NioEventLoopGroup.shutdownQuick(): Future<*> {
    return this.shutdownGracefully(100, 1000, TimeUnit.MILLISECONDS)
}

fun String.toByteBuf(charset: Charset = Charsets.UTF_8): ByteBuf = Unpooled.copiedBuffer(this, charset)

fun ByteBuf.skipAll() {
    this.skipBytes(this.readableBytes())
}

val ByteBuf.isEmpty: Boolean get() = this.readableBytes() == 0
val ByteBuf.isNotEmpty: Boolean get() = this.readableBytes() > 0
val ByteBuf.size: Int get() = this.readableBytes()
val ByteBuf.stringCopy: String get() = this.toString(Charsets.UTF_8)
val ByteBuf.bytesCopy: ByteArray
    get() = ByteArray(this.readableBytes()).also { buf ->
        getBytes(readerIndex(), buf)
    }
val ByteBuf.bytesList: BytesList get() = BytesList(this)

class BytesList(val buf: ByteBuf) : AbstractList<Byte>() {
    override val size: Int get() = buf.readableBytes()
    override fun get(index: Int): Byte = buf.getByte(buf.readerIndex() + index)
    fun string(charset: Charset = Charsets.UTF_8): String = String(this.toByteArray(), charset)

    fun clearBuf() {
        buf.skipBytes(buf.readableBytes())
    }

    fun releaseBuf() {
        buf.release()
    }

}
