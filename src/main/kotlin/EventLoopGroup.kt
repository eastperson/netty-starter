
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.ReferenceCountUtil
import java.net.InetSocketAddress

fun main() {
    val eventLoopGroup = NioEventLoopGroup()
    val nioServerSocketChannel = NioServerSocketChannel()
    eventLoopGroup.register(nioServerSocketChannel)

    val bind = nioServerSocketChannel.bind(InetSocketAddress(8080))
    bind.addListener {
        ChannelFutureListener { future ->
            println(future.channel())
            future.channel().pipeline()
                .addLast(object : ChannelInboundHandlerAdapter() {
                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                        val channel = msg as Channel
                        println("channel : $channel")
                        val `in` = msg as ByteBuf
                        try {
                            while (`in`.isReadable) { // (1)
                                print(Char(`in`.readByte().toUShort()))
                                System.out.flush()
                            }
                        } finally {
                            ReferenceCountUtil.release(msg) // (2)
                        }
                    }
                })
        }
    }
}
