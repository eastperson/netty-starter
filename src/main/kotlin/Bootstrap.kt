import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.CharsetUtil

fun main() {
    val serverBootstrap = ServerBootstrap()

    val parent = NioEventLoopGroup()
    val child = NioEventLoopGroup()
    parent.execute { println("hello parent") }
    child.execute { println("hello child") }

    serverBootstrap.group(parent, child)
        .channel(NioServerSocketChannel::class.java)
        .localAddress(8080) // 포트 바인딩
        .childHandler(object : SimpleChannelInboundHandler<ByteBuf>() {
            override fun channelRead0(ctx: ChannelHandlerContext?, msg: ByteBuf?) {
                print("[${Thread.currentThread().name}] msg : ${msg?.toString(CharsetUtil.UTF_8)}")
            }
        })

    val channelFuture = serverBootstrap.bind()
}
