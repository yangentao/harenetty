package io.github.yangentao.harenetty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import kotlin.jvm.java

abstract class BaseNettyServer(val port: Int, val bossCount: Int = 8, val workerCount: Int = 12) {
    private var boss: NioEventLoopGroup? = null
    private var worker: NioEventLoopGroup? = null
    private var channelFuture: ChannelFuture? = null

    val running: Boolean get() = boss != null

    protected abstract fun initChannels(ch: SocketChannel)

    protected open fun onStart() {}
    protected open fun afterStart() {}
    protected open fun onStop() {}
    protected open fun afterStop() {}

    fun start() {
        assert(!running)
        onStart()
        val bossGroup = NioEventLoopGroup(bossCount)
        val workerGroup = NioEventLoopGroup(workerCount)
        try {
            val boot = ServerBootstrap()
            boot.group(bossGroup, workerGroup)
            boot.channel(NioServerSocketChannel::class.java)
            boot.childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    initChannels(ch)
                }
            })
            val fu = boot.bind(port).sync()
            boss = bossGroup
            worker = workerGroup
            channelFuture = fu
            afterStart()
        } catch (ex: Exception) {
            ex.printStackTrace()
            workerGroup.shutdownQuick()
            bossGroup.shutdownQuick()
        }
    }

    fun stop() {
        if (boss != null) {
            onStop()
            channelFuture?.channel()?.close()
            worker?.shutdownQuick()?.sync()
            boss?.shutdownQuick()?.sync()
            boss = null
            worker = null
            afterStop()
        }
    }

    fun waitClose() {
        val fu = channelFuture ?: return
        fu.channel().closeFuture().sync()
        channelFuture = null
    }

}